package com.google.mlkit.vision.digitalink.recognition;

public final class DigitalInkRecognition {
    private DigitalInkRecognition() {
    }

    public static DigitalInkRecognizer getClient(DigitalInkRecognizerOptions options) {
        return new DigitalInkRecognizer();
    }
}
