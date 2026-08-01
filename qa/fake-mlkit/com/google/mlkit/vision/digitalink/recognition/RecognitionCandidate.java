package com.google.mlkit.vision.digitalink.recognition;

public final class RecognitionCandidate {
    private final String text;

    public RecognitionCandidate(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
