package com.mohnish.aircanvas.ink;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Lifecycle-safe Smart Ink recognizer.
 *
 * <p>ML Kit Digital Ink is loaded reflectively so dependency packaging changes
 * cannot break normal APK compilation. If the library is unavailable, the app
 * keeps geometric Smart Ink cleanup and reports a clear fallback message.</p>
 */
public final class OnDeviceInkRecognizer implements AutoCloseable {
    public interface Callback {
        void onStatus(String status);

        void onRecognized(SmartInkRequest request, String text);

        void onFailure(SmartInkRequest request, String message);
    }

    private static final int MAX_PENDING = 12;
    private static final String PACKAGE = "com.google.mlkit.vision.digitalink.recognition.";

    private final Deque<Pending> pending = new ArrayDeque<>();
    private final MlKitBridge bridge;

    private boolean modelReady;
    private boolean modelPreparing;
    private boolean recognitionInFlight;
    private boolean closed;
    private Callback preparationCallback;

    public OnDeviceInkRecognizer() {
        bridge = MlKitBridge.tryCreate();
    }

    public synchronized void prepare(Callback callback) {
        if (callback == null || closed || modelReady || modelPreparing) {
            return;
        }
        ensureModel(callback);
    }

    public synchronized void recognize(
            SmartInkRequest request,
            String preContext,
            Callback callback
    ) {
        if (request == null || callback == null || closed) {
            return;
        }
        if (!bridge.available()) {
            callback.onFailure(
                    request,
                    "Handwriting recognizer unavailable; the original stroke was kept."
            );
            return;
        }
        if (pending.size() >= MAX_PENDING) {
            Pending dropped = pending.removeFirst();
            dropped.callback.onFailure(
                    dropped.request,
                    "Smart Ink queue was full; the original stroke was kept."
            );
        }
        pending.addLast(new Pending(request, trimContext(preContext), callback));
        if (modelReady) {
            pump();
        } else {
            ensureModel(callback);
        }
    }

    private synchronized void ensureModel(Callback callback) {
        if (closed || modelReady || modelPreparing) {
            return;
        }
        if (!bridge.available()) {
            callback.onStatus("Handwriting recognizer unavailable; shape cleanup still works");
            return;
        }
        modelPreparing = true;
        preparationCallback = callback;
        callback.onStatus("Checking Smart Ink model...");
        bridge.isModelDownloaded(
                downloaded -> {
                    if (Boolean.TRUE.equals(downloaded)) {
                        markModelReady();
                    } else {
                        callback.onStatus("Downloading handwriting model once (~20 MB)...");
                        bridge.downloadModel(
                                ignored -> markModelReady(),
                                this::failModelPreparation
                        );
                    }
                },
                this::failModelPreparation
        );
    }

    private synchronized void markModelReady() {
        if (closed) {
            return;
        }
        modelPreparing = false;
        modelReady = true;
        if (preparationCallback != null) {
            preparationCallback.onStatus("Smart Ink ready");
            preparationCallback = null;
        }
        for (Pending item : pending) {
            item.callback.onStatus("Smart Ink ready");
        }
        pump();
    }

    private synchronized void failModelPreparation(Exception exception) {
        modelPreparing = false;
        String message = "Handwriting model unavailable: " + safeMessage(exception);
        if (preparationCallback != null) {
            preparationCallback.onStatus(message);
            preparationCallback = null;
        }
        while (!pending.isEmpty()) {
            Pending item = pending.removeFirst();
            item.callback.onFailure(item.request, message);
        }
    }

    private synchronized void pump() {
        if (closed || !modelReady || recognitionInFlight || pending.isEmpty()) {
            return;
        }
        Pending item = pending.removeFirst();
        recognitionInFlight = true;
        try {
            Object ink = bridge.buildInk(item.request);
            Object context = bridge.buildContext(item.request, item.preContext);
            bridge.recognize(
                    ink,
                    context,
                    rawText -> {
                        String cleaned = cleanText(rawText);
                        if (cleaned.isEmpty()) {
                            item.callback.onFailure(
                                    item.request,
                                    "No confident text match; the original stroke was kept."
                            );
                        } else {
                            item.callback.onRecognized(item.request, cleaned);
                        }
                        finishRecognition();
                    },
                    exception -> {
                        item.callback.onFailure(
                                item.request,
                                "Could not clean this handwriting: " + safeMessage(exception)
                        );
                        finishRecognition();
                    }
            );
        } catch (ReflectiveOperationException | RuntimeException exception) {
            item.callback.onFailure(
                    item.request,
                    "Could not prepare handwriting recognition: " + safeMessage(exception)
            );
            finishRecognition();
        }
    }

    private synchronized void finishRecognition() {
        recognitionInFlight = false;
        pump();
    }

    private static String trimContext(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.substring(Math.max(0, value.length() - 20));
    }

    private static String cleanText(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim()
                .replaceAll("\\s{2,}", " ");
        return cleaned.substring(0, Math.min(cleaned.length(), 160));
    }

    private static String safeMessage(Throwable throwable) {
        Throwable cause = throwable;
        if (throwable instanceof InvocationTargetException invocation
                && invocation.getTargetException() != null) {
            cause = invocation.getTargetException();
        }
        String message = cause == null ? null : cause.getMessage();
        return message == null || message.isBlank()
                ? "check connection and try again"
                : message;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        preparationCallback = null;
        pending.clear();
        bridge.close();
    }

    private record Pending(
            SmartInkRequest request,
            String preContext,
            Callback callback
    ) {
    }

    private interface SuccessListener {
        void onSuccess(Object value);
    }

    private interface FailureListener {
        void onFailure(Exception exception);
    }

    private interface TextListener {
        void onText(String text);
    }

    private static final class MlKitBridge {
        private final Class<?> inkClass;
        private final Class<?> inkPointClass;
        private final Class<?> writingAreaClass;
        private final Class<?> recognitionContextClass;
        private final Object model;
        private final Object recognizer;
        private final Object modelManager;
        private final Method isModelDownloaded;
        private final Method download;
        private final Method recognizeWithContext;
        private final Method close;

        private MlKitBridge(
                Class<?> inkClass,
                Class<?> inkPointClass,
                Class<?> writingAreaClass,
                Class<?> recognitionContextClass,
                Object model,
                Object recognizer,
                Object modelManager,
                Method isModelDownloaded,
                Method download,
                Method recognizeWithContext,
                Method close
        ) {
            this.inkClass = inkClass;
            this.inkPointClass = inkPointClass;
            this.writingAreaClass = writingAreaClass;
            this.recognitionContextClass = recognitionContextClass;
            this.model = model;
            this.recognizer = recognizer;
            this.modelManager = modelManager;
            this.isModelDownloaded = isModelDownloaded;
            this.download = download;
            this.recognizeWithContext = recognizeWithContext;
            this.close = close;
        }

        static MlKitBridge tryCreate() {
            try {
                Class<?> identifierClass = Class.forName(PACKAGE
                        + "DigitalInkRecognitionModelIdentifier");
                Class<?> modelClass = Class.forName(PACKAGE + "DigitalInkRecognitionModel");
                Class<?> recognitionClass = Class.forName(PACKAGE + "DigitalInkRecognition");
                Class<?> optionsClass = Class.forName(PACKAGE + "DigitalInkRecognizerOptions");
                Class<?> recognizerClass = Class.forName(PACKAGE + "DigitalInkRecognizer");
                Class<?> inkClass = Class.forName(PACKAGE + "Ink");
                Class<?> inkPointClass = Class.forName(PACKAGE + "Ink$Point");
                Class<?> recognitionContextClass = Class.forName(PACKAGE + "RecognitionContext");
                Class<?> writingAreaClass = Class.forName(PACKAGE + "WritingArea");
                Class<?> downloadConditionsClass = Class.forName(
                        "com.google.mlkit.common.model.DownloadConditions");
                Class<?> remoteModelManagerClass = Class.forName(
                        "com.google.mlkit.common.model.RemoteModelManager");
                Class<?> remoteModelClass = Class.forName(
                        "com.google.mlkit.common.model.RemoteModel");

                Object identifier = identifierClass
                        .getMethod("fromLanguageTag", String.class)
                        .invoke(null, "en-US");
                if (identifier == null) {
                    return unavailable();
                }
                Object model = modelClass
                        .getMethod("builder", identifierClass)
                        .invoke(null, identifier);
                model = model.getClass().getMethod("build").invoke(model);
                Object options = optionsClass
                        .getMethod("builder", modelClass)
                        .invoke(null, model);
                options = options.getClass().getMethod("build").invoke(options);
                Object recognizer = recognitionClass
                        .getMethod("getClient", optionsClass)
                        .invoke(null, options);
                Object manager = remoteModelManagerClass
                        .getMethod("getInstance")
                        .invoke(null);
                Method isDownloaded = remoteModelManagerClass
                        .getMethod("isModelDownloaded", remoteModelClass);
                Method download = remoteModelManagerClass
                        .getMethod("download", remoteModelClass, downloadConditionsClass);
                Method recognize = recognizerClass
                        .getMethod("recognize", inkClass, recognitionContextClass);
                Method close = recognizerClass.getMethod("close");
                return new MlKitBridge(
                        inkClass,
                        inkPointClass,
                        writingAreaClass,
                        recognitionContextClass,
                        model,
                        recognizer,
                        manager,
                        isDownloaded,
                        download,
                        recognize,
                        close
                );
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return unavailable();
            }
        }

        static MlKitBridge unavailable() {
            return new MlKitBridge(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        boolean available() {
            return recognizer != null;
        }

        void isModelDownloaded(SuccessListener success, FailureListener failure) {
            invokeTask(isModelDownloaded, modelManager, new Object[]{model}, success, failure);
        }

        void downloadModel(SuccessListener success, FailureListener failure) {
            try {
                Class<?> downloadConditionsClass = Class.forName(
                        "com.google.mlkit.common.model.DownloadConditions");
                Object conditions = Class.forName(
                                "com.google.mlkit.common.model.DownloadConditions$Builder")
                        .getConstructor()
                        .newInstance();
                conditions = conditions.getClass().getMethod("build").invoke(conditions);
                invokeTask(download, modelManager, new Object[]{model, conditions}, success, failure);
            } catch (ReflectiveOperationException exception) {
                failure.onFailure(exception);
            }
        }

        Object buildInk(SmartInkRequest request) throws ReflectiveOperationException {
            Object inkBuilder = inkClass.getMethod("builder").invoke(null);
            Class<?> strokeClass = Class.forName(PACKAGE + "Ink$Stroke");
            Method addStroke = inkBuilder.getClass().getMethod("addStroke", strokeClass);
            Method pointCreate = inkPointClass.getMethod(
                    "create",
                    float.class,
                    float.class,
                    long.class
            );
            long timestamp = Math.max(1L, request.completedAtMs - 24L * request.strokeCount());
            float originX = request.bounds.left;
            float originY = request.bounds.top;

            for (java.util.List<Float> points : request.strokes) {
                if (points.size() < 4) {
                    continue;
                }
                Object strokeBuilder = strokeClass.getMethod("builder").invoke(null);
                Method addPoint = strokeBuilder.getClass().getMethod("addPoint", inkPointClass);
                int pointCount = points.size() / 2;
                int pointStride = Math.max(1, (int) Math.ceil(pointCount / 384d));
                int lastAdded = -1;
                for (int pointIndex = 0; pointIndex < pointCount; pointIndex += pointStride) {
                    int index = pointIndex * 2;
                    float x = points.get(index) - originX + 24f;
                    float y = points.get(index + 1) - originY + 24f;
                    addPoint.invoke(strokeBuilder, pointCreate.invoke(null, x, y, timestamp));
                    timestamp += 16L * pointStride;
                    lastAdded = pointIndex;
                }
                if (lastAdded != pointCount - 1) {
                    int index = (pointCount - 1) * 2;
                    addPoint.invoke(strokeBuilder, pointCreate.invoke(
                            null,
                            points.get(index) - originX + 24f,
                            points.get(index + 1) - originY + 24f,
                            timestamp
                    ));
                    timestamp += 16L;
                }
                Object stroke = strokeBuilder.getClass().getMethod("build").invoke(strokeBuilder);
                addStroke.invoke(inkBuilder, stroke);
                timestamp += 28L;
            }
            return inkBuilder.getClass().getMethod("build").invoke(inkBuilder);
        }

        Object buildContext(
                SmartInkRequest request,
                String preContext
        ) throws ReflectiveOperationException {
            float width = Math.max(180f, request.bounds.width() + 48f);
            float height = Math.max(110f, request.bounds.height() + 48f);
            Object area = writingAreaClass
                    .getConstructor(float.class, float.class)
                    .newInstance(width, height);
            Object context = recognitionContextClass.getMethod("builder").invoke(null);
            context.getClass().getMethod("setPreContext", String.class).invoke(context, preContext);
            context.getClass().getMethod("setWritingArea", writingAreaClass).invoke(context, area);
            return context.getClass().getMethod("build").invoke(context);
        }

        void recognize(
                Object ink,
                Object context,
                TextListener success,
                FailureListener failure
        ) {
            invokeTask(
                    recognizeWithContext,
                    recognizer,
                    new Object[]{ink, context},
                    result -> success.onText(extractText(result)),
                    failure
            );
        }

        void close() {
            if (close == null || recognizer == null) {
                return;
            }
            try {
                close.invoke(recognizer);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // Closing is best-effort; the app is already leaving recognition.
            }
        }

        private static String extractText(Object result) {
            try {
                Object candidates = result.getClass().getMethod("getCandidates").invoke(result);
                if (candidates instanceof java.util.List<?> list && !list.isEmpty()) {
                    Object first = list.get(0);
                    Object text = first.getClass().getMethod("getText").invoke(first);
                    return text == null ? "" : String.valueOf(text);
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall through to empty text.
            }
            return "";
        }

        private static void invokeTask(
                Method method,
                Object target,
                Object[] args,
                SuccessListener success,
                FailureListener failure
        ) {
            try {
                Object task = method.invoke(target, args);
                Object successProxy = java.lang.reflect.Proxy.newProxyInstance(
                        OnDeviceInkRecognizer.class.getClassLoader(),
                        new Class<?>[]{Class.forName("com.google.android.gms.tasks.OnSuccessListener")},
                        (proxy, called, calledArgs) -> {
                            if ("onSuccess".equals(called.getName())) {
                                success.onSuccess(calledArgs == null ? null : calledArgs[0]);
                            }
                            return null;
                        }
                );
                Object failureProxy = java.lang.reflect.Proxy.newProxyInstance(
                        OnDeviceInkRecognizer.class.getClassLoader(),
                        new Class<?>[]{Class.forName("com.google.android.gms.tasks.OnFailureListener")},
                        (proxy, called, calledArgs) -> {
                            if ("onFailure".equals(called.getName())) {
                                Object value = calledArgs == null ? null : calledArgs[0];
                                failure.onFailure(value instanceof Exception
                                        ? (Exception) value
                                        : new IllegalStateException(String.valueOf(value)));
                            }
                            return null;
                        }
                );
                task = task.getClass()
                        .getMethod(
                                "addOnSuccessListener",
                                Class.forName("com.google.android.gms.tasks.OnSuccessListener")
                        )
                        .invoke(task, successProxy);
                task.getClass()
                        .getMethod(
                                "addOnFailureListener",
                                Class.forName("com.google.android.gms.tasks.OnFailureListener")
                        )
                        .invoke(task, failureProxy);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                failure.onFailure(exception);
            }
        }
    }
}
