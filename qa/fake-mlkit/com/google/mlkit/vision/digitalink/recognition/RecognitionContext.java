package com.google.mlkit.vision.digitalink.recognition;

public final class RecognitionContext {
    public final String preContext;
    public final WritingArea writingArea;

    private RecognitionContext(String preContext, WritingArea writingArea) {
        this.preContext = preContext;
        this.writingArea = writingArea;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String preContext = "";
        private WritingArea writingArea;

        public Builder setPreContext(String value) {
            preContext = value;
            return this;
        }

        public Builder setWritingArea(WritingArea value) {
            writingArea = value;
            return this;
        }

        public RecognitionContext build() {
            return new RecognitionContext(preContext, writingArea);
        }
    }
}
