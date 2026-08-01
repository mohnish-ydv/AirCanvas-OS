package com.google.mlkit.vision.digitalink.recognition;

import com.google.mlkit.common.model.RemoteModel;

public final class DigitalInkRecognitionModel implements RemoteModel {
    private final DigitalInkRecognitionModelIdentifier identifier;

    private DigitalInkRecognitionModel(DigitalInkRecognitionModelIdentifier identifier) {
        this.identifier = identifier;
    }

    public static Builder builder(DigitalInkRecognitionModelIdentifier identifier) {
        return new Builder(identifier);
    }

    public DigitalInkRecognitionModelIdentifier identifier() {
        return identifier;
    }

    public static final class Builder {
        private final DigitalInkRecognitionModelIdentifier identifier;

        private Builder(DigitalInkRecognitionModelIdentifier identifier) {
            this.identifier = identifier;
        }

        public DigitalInkRecognitionModel build() {
            return new DigitalInkRecognitionModel(identifier);
        }
    }
}
