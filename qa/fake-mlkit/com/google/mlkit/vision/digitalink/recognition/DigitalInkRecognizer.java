package com.google.mlkit.vision.digitalink.recognition;

import com.google.android.gms.tasks.Task;

public final class DigitalInkRecognizer {
    public static Ink lastInk;
    public static RecognitionContext lastContext;
    public static boolean closed;

    public Task<RecognitionResult> recognize(Ink ink, RecognitionContext context) {
        lastInk = ink;
        lastContext = context;
        return new Task<>(new RecognitionResult("AIR CANVAS"));
    }

    public void close() {
        closed = true;
    }
}
