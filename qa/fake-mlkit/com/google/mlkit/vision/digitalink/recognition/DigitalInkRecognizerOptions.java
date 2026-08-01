package com.google.mlkit.vision.digitalink.recognition;

public final class DigitalInkRecognizerOptions {
    private final DigitalInkRecognitionModel model;

    private DigitalInkRecognizerOptions(DigitalInkRecognitionModel model) {
        this.model = model;
    }

    public static Builder builder(DigitalInkRecognitionModel model) {
        return new Builder(model);
    }

    public DigitalInkRecognitionModel model() {
        return model;
    }

    public static final class Builder {
        private final DigitalInkRecognitionModel model;

        private Builder(DigitalInkRecognitionModel model) {
            this.model = model;
        }

        public DigitalInkRecognizerOptions build() {
            return new DigitalInkRecognizerOptions(model);
        }
    }
}
