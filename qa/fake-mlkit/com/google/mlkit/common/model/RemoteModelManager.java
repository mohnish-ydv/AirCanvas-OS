package com.google.mlkit.common.model;

import com.google.android.gms.tasks.Task;

public final class RemoteModelManager {
    private static final RemoteModelManager INSTANCE = new RemoteModelManager();

    private RemoteModelManager() {
    }

    public static RemoteModelManager getInstance() {
        return INSTANCE;
    }

    public Task<Boolean> isModelDownloaded(RemoteModel model) {
        return new Task<>(Boolean.TRUE);
    }

    public Task<Void> download(RemoteModel model, DownloadConditions conditions) {
        return new Task<>((Void) null);
    }
}
