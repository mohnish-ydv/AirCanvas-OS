package com.google.mlkit.vision.digitalink.recognition;

public final class DigitalInkRecognitionModelIdentifier {
    private final String languageTag;

    private DigitalInkRecognitionModelIdentifier(String languageTag) {
        this.languageTag = languageTag;
    }

    public static DigitalInkRecognitionModelIdentifier fromLanguageTag(String languageTag) {
        return languageTag == null || languageTag.isBlank()
                ? null
                : new DigitalInkRecognitionModelIdentifier(languageTag);
    }

    public String languageTag() {
        return languageTag;
    }
}
