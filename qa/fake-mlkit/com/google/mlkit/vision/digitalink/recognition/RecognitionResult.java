package com.google.mlkit.vision.digitalink.recognition;

import java.util.List;

public final class RecognitionResult {
    private final List<RecognitionCandidate> candidates;

    public RecognitionResult(String text) {
        candidates = List.of(new RecognitionCandidate(text));
    }

    public List<RecognitionCandidate> getCandidates() {
        return candidates;
    }
}
