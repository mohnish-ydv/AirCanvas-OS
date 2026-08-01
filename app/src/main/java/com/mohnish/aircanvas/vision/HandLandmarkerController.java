package com.mohnish.aircanvas.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.Display;
import android.view.Surface;
import android.os.SystemClock;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.mohnish.aircanvas.gesture.LandmarkPoint;
import com.mohnish.aircanvas.settings.AppSettings;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class HandLandmarkerController implements AutoCloseable {
    public interface Listener {
        void onReady();

        void onHands(
                List<List<LandmarkPoint>> hands,
                int inputWidth,
                int inputHeight,
                long inferenceMs
        );

        void onStatus(String status);

        void onError(String message);
    }

    private static final String MODEL_ASSET = "hand_landmarker.task";

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final PreviewView previewView;
    private final AppSettings settings;
    private final Listener listener;
    private final ExecutorService analysisExecutor;
    private final AtomicBoolean frameInFlight = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong lastTimestamp = new AtomicLong(0L);
    private final AtomicLong transformGeneration = new AtomicLong(0L);
    private final AdaptiveFrameGovernor frameGovernor;

    private ProcessCameraProvider cameraProvider;
    private Preview previewUseCase;
    private ImageAnalysis analysisUseCase;
    private HandLandmarker handLandmarker;
    private Bitmap inFlightBitmap;
    private Bitmap reusableOrientedBitmap;
    private Bitmap reusableSourceBitmap;
    private Bitmap reusablePaddedBitmap;
    private final Matrix orientationMatrix = new Matrix();
    private final Canvas bitmapCanvas = new Canvas();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private int inFlightOutputWidth = 1;
    private int inFlightOutputHeight = 1;
    private int activeLensFacing = CameraSelector.LENS_FACING_FRONT;
    private long lastSubmittedAt;
    private long inFlightGeneration;

    public HandLandmarkerController(
            Context context,
            LifecycleOwner lifecycleOwner,
            PreviewView previewView,
            AppSettings settings,
            Listener listener
    ) {
        this.context = context.getApplicationContext();
        this.lifecycleOwner = lifecycleOwner;
        this.previewView = previewView;
        this.settings = settings;
        this.listener = listener;
        this.frameGovernor = new AdaptiveFrameGovernor(
                settings.performanceProfile().frameIntervalMs
        );
        this.analysisExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "aircanvas-hand-analysis");
            thread.setPriority(Thread.NORM_PRIORITY + 1);
            return thread;
        });
    }

    public void start() {
        listener.onStatus("Loading on-device hand model…");
        previewView.setImplementationMode(PreviewView.ImplementationMode.PERFORMANCE);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        analysisExecutor.execute(() -> {
            if (closed.get()) {
                return;
            }
            try {
                createLandmarker();
                ContextCompat.getMainExecutor(context).execute(this::bindCamera);
            } catch (RuntimeException exception) {
                listener.onError("Hand model could not start: " + safeMessage(exception));
            }
        });
    }

    private void createLandmarker() {
        BaseOptions baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.CPU)
                .setModelAssetPath(MODEL_ASSET)
                .build();

        HandLandmarker.HandLandmarkerOptions options =
                HandLandmarker.HandLandmarkerOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setMinHandDetectionConfidence(0.45f)
                        .setMinHandPresenceConfidence(0.45f)
                        .setMinTrackingConfidence(0.50f)
                        .setNumHands(2)
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setResultListener(this::onResult)
                        .setErrorListener(this::onLandmarkerError)
                        .build();

        handLandmarker = HandLandmarker.createFromOptions(context, options);
    }

    private void bindCamera() {
        if (closed.get()) {
            return;
        }
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            if (closed.get()) {
                return;
            }
            try {
                cameraProvider = future.get();
                cameraProvider.unbindAll();

                int targetRotation = currentDisplayRotation();
                previewUseCase = new Preview.Builder()
                        .setTargetRotation(targetRotation)
                        .build();
                previewUseCase.setSurfaceProvider(previewView.getSurfaceProvider());

                analysisUseCase = new ImageAnalysis.Builder()
                        .setTargetRotation(targetRotation)
                        .setOutputImageRotationEnabled(false)
                        .setResolutionSelector(
                                new ResolutionSelector.Builder()
                                        .setResolutionStrategy(new ResolutionStrategy(
                                                new Size(512, 384),
                                                ResolutionStrategy
                                                        .FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                        ))
                                        .build()
                        )
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();
                analysisUseCase.setAnalyzer(analysisExecutor, this::analyze);

                CameraSelector selector = cameraSelector(settings.lensFacing());
                cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        previewUseCase,
                        analysisUseCase
                );
                listener.onReady();
                listener.onStatus("Show one hand to begin");
            } catch (Exception exception) {
                listener.onError("Camera could not start: " + safeMessage(exception));
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private CameraSelector cameraSelector(int preferredFacing)
            throws CameraInfoUnavailableException {
        int facing = preferredFacing == CameraSelector.LENS_FACING_BACK
                ? CameraSelector.LENS_FACING_BACK
                : CameraSelector.LENS_FACING_FRONT;
        if (cameraProvider != null && cameraProvider.hasCamera(
                new CameraSelector.Builder().requireLensFacing(facing).build()
        )) {
            activeLensFacing = facing;
            return new CameraSelector.Builder().requireLensFacing(facing).build();
        }
        int fallback = facing == CameraSelector.LENS_FACING_FRONT
                ? CameraSelector.LENS_FACING_BACK
                : CameraSelector.LENS_FACING_FRONT;
        activeLensFacing = fallback;
        return new CameraSelector.Builder().requireLensFacing(fallback).build();
    }

    private void analyze(@NonNull ImageProxy imageProxy) {
        if (closed.get() || handLandmarker == null) {
            imageProxy.close();
            return;
        }
        long now = SystemClock.uptimeMillis();
        frameGovernor.setBaseInterval(settings.performanceProfile().frameIntervalMs);
        long interval = frameGovernor.intervalMs();
        if (now - lastSubmittedAt < interval || !frameInFlight.compareAndSet(false, true)) {
            imageProxy.close();
            return;
        }
        lastSubmittedAt = now;

        MPImage input = null;
        Bitmap source = null;
        Bitmap oriented = null;
        boolean proxyClosed = false;
        try {
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            source = imageProxyToBitmap(imageProxy);
            imageProxy.close();
            proxyClosed = true;

            FrameOrientation orientation = new FrameOrientation(
                    source.getWidth(),
                    source.getHeight(),
                    rotation,
                    activeLensFacing == CameraSelector.LENS_FACING_FRONT
            );
            oriented = orientForPreview(source, orientation);
            // orientForPreview owns/recycles the source when it creates a copy.
            source = null;

            input = new BitmapImageBuilder(oriented).build();
            synchronized (this) {
                inFlightBitmap = oriented;
                inFlightOutputWidth = oriented.getWidth();
                inFlightOutputHeight = oriented.getHeight();
                inFlightGeneration = transformGeneration.get();
            }
            oriented = null; // ownership transferred to the in-flight result

            long timestamp = Math.max(now, lastTimestamp.incrementAndGet());
            lastTimestamp.set(timestamp);
            ImageProcessingOptions processingOptions =
                    ImageProcessingOptions.builder()
                            .setRotationDegrees(0)
                            .build();
            handLandmarker.detectAsync(input, processingOptions, timestamp);
        } catch (RuntimeException exception) {
            if (input != null) {
                input.close();
            }
            recycle(source);
            recycle(oriented);
            if (!proxyClosed) {
                imageProxy.close();
            }
            releaseInFlightBitmap();
            frameInFlight.set(false);
            listener.onError("Camera frame failed: " + safeMessage(exception));
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        ImageProxy.PlaneProxy plane = imageProxy.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        buffer.rewind();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = Math.max(0, rowStride - pixelStride * imageProxy.getWidth());
        int paddedWidth = imageProxy.getWidth() + rowPadding / Math.max(1, pixelStride);

        if (paddedWidth == imageProxy.getWidth()) {
            Bitmap source = acquireReusableSourceBitmap(
                    imageProxy.getWidth(),
                    imageProxy.getHeight()
            );
            source.copyPixelsFromBuffer(buffer);
            return source;
        }

        Bitmap padded = acquireReusablePaddedBitmap(
                paddedWidth,
                imageProxy.getHeight()
        );
        padded.copyPixelsFromBuffer(buffer);
        Bitmap cropped = acquireReusableSourceBitmap(
                imageProxy.getWidth(),
                imageProxy.getHeight()
        );
        cropped.eraseColor(0x00000000);
        bitmapCanvas.setBitmap(cropped);
        bitmapCanvas.drawBitmap(padded, 0f, 0f, bitmapPaint);
        bitmapCanvas.setBitmap(null);
        releasePaddedBitmap(padded);
        return cropped;
    }

    private Bitmap orientForPreview(
            Bitmap source,
            FrameOrientation orientation
    ) {
        if (orientation.rotationDegrees == 0 && !orientation.mirrorHorizontally) {
            return source;
        }

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int outputWidth = orientation.outputWidth();
        int outputHeight = orientation.outputHeight();

        orientationMatrix.setValues(orientation.matrixValues());
        Bitmap output = acquireReusableBitmap(outputWidth, outputHeight);
        output.eraseColor(0x00000000);
        bitmapCanvas.setBitmap(output);
        bitmapCanvas.drawBitmap(source, orientationMatrix, bitmapPaint);
        bitmapCanvas.setBitmap(null);
        releaseSourceBitmap(source);
        return output;
    }

    private synchronized Bitmap acquireReusableSourceBitmap(int width, int height) {
        if (isReusable(reusableSourceBitmap, width, height)) {
            Bitmap result = reusableSourceBitmap;
            reusableSourceBitmap = null;
            return result;
        }
        recycle(reusableSourceBitmap);
        reusableSourceBitmap = null;

        // A non-mirrored frame is returned through the oriented slot. Reuse it
        // here rather than allocating another ~0.75 MB bitmap every frame.
        if (isReusable(reusableOrientedBitmap, width, height)) {
            Bitmap result = reusableOrientedBitmap;
            reusableOrientedBitmap = null;
            return result;
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    private synchronized Bitmap acquireReusablePaddedBitmap(int width, int height) {
        if (isReusable(reusablePaddedBitmap, width, height)) {
            Bitmap result = reusablePaddedBitmap;
            reusablePaddedBitmap = null;
            return result;
        }
        recycle(reusablePaddedBitmap);
        reusablePaddedBitmap = null;
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    private synchronized void releaseSourceBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        if (!closed.get() && reusableSourceBitmap == null) {
            reusableSourceBitmap = bitmap;
        } else {
            recycle(bitmap);
        }
    }

    private synchronized void releasePaddedBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        if (!closed.get() && reusablePaddedBitmap == null) {
            reusablePaddedBitmap = bitmap;
        } else {
            recycle(bitmap);
        }
    }

    private static boolean isReusable(Bitmap bitmap, int width, int height) {
        return bitmap != null
                && !bitmap.isRecycled()
                && bitmap.getWidth() == width
                && bitmap.getHeight() == height;
    }

    private synchronized Bitmap acquireReusableBitmap(int width, int height) {
        if (reusableOrientedBitmap != null) {
            if (!reusableOrientedBitmap.isRecycled()
                    && reusableOrientedBitmap.getWidth() == width
                    && reusableOrientedBitmap.getHeight() == height) {
                Bitmap result = reusableOrientedBitmap;
                reusableOrientedBitmap = null;
                return result;
            }
            recycle(reusableOrientedBitmap);
            reusableOrientedBitmap = null;
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private int currentDisplayRotation() {
        Display display = previewView.getDisplay();
        return display == null ? Surface.ROTATION_0 : display.getRotation();
    }

    /** Refreshes CameraX target rotation after an OEM/display configuration change. */
    public void updateTargetRotation() {
        transformGeneration.incrementAndGet();
        int rotation = currentDisplayRotation();
        if (previewUseCase != null) {
            previewUseCase.setTargetRotation(rotation);
        }
        if (analysisUseCase != null) {
            analysisUseCase.setTargetRotation(rotation);
        }
    }

    private void onResult(HandLandmarkerResult result, MPImage input) {
        int inputWidth;
        int inputHeight;
        long resultGeneration;
        synchronized (this) {
            inputWidth = inFlightOutputWidth;
            inputHeight = inFlightOutputHeight;
            resultGeneration = inFlightGeneration;
        }
        input.close();
        releaseInFlightBitmap();
        frameInFlight.set(false);
        if (closed.get() || resultGeneration != transformGeneration.get()) {
            return;
        }
        List<List<LandmarkPoint>> hands = new ArrayList<>();
        for (List<NormalizedLandmark> rawHand : result.landmarks()) {
            List<LandmarkPoint> hand = new ArrayList<>(rawHand.size());
            for (NormalizedLandmark point : rawHand) {
                hand.add(new LandmarkPoint(
                        point.x(),
                        point.y(),
                        point.z()
                ));
            }
            hands.add(hand);
        }
        long inference = Math.max(0L, SystemClock.uptimeMillis() - result.timestampMs());
        frameGovernor.recordInference(inference);
        listener.onHands(hands, inputWidth, inputHeight, inference);
    }

    private void onLandmarkerError(RuntimeException error) {
        releaseInFlightBitmap();
        frameInFlight.set(false);
        if (!closed.get()) {
            listener.onError("Hand tracking error: " + safeMessage(error));
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
    }

    private synchronized void releaseInFlightBitmap() {
        if (inFlightBitmap != null && !inFlightBitmap.isRecycled()) {
            if (!closed.get() && reusableOrientedBitmap == null) {
                reusableOrientedBitmap = inFlightBitmap;
            } else {
                recycle(inFlightBitmap);
            }
        }
        inFlightBitmap = null;
        inFlightOutputWidth = 1;
        inFlightOutputHeight = 1;
        inFlightGeneration = transformGeneration.get();
    }

    private synchronized void releaseReusableBitmap() {
        recycle(reusableOrientedBitmap);
        reusableOrientedBitmap = null;
        recycle(reusableSourceBitmap);
        reusableSourceBitmap = null;
        recycle(reusablePaddedBitmap);
        reusablePaddedBitmap = null;
        bitmapCanvas.setBitmap(null);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (cameraProvider != null) {
            ContextCompat.getMainExecutor(context).execute(() -> {
                cameraProvider.unbindAll();
                previewUseCase = null;
                analysisUseCase = null;
            });
        }
        analysisExecutor.execute(() -> {
            if (handLandmarker != null) {
                handLandmarker.close();
                handLandmarker = null;
            }
            releaseInFlightBitmap();
            releaseReusableBitmap();
        });
        analysisExecutor.shutdown();
    }
}
