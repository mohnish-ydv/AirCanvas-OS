package com.google.android.gms.tasks;

public final class Task<T> {
    private final T value;
    private final Exception failure;

    public Task(T value) {
        this.value = value;
        this.failure = null;
    }

    public Task(Exception failure) {
        this.value = null;
        this.failure = failure;
    }

    public Task<T> addOnSuccessListener(OnSuccessListener<? super T> listener) {
        if (failure == null) {
            listener.onSuccess(value);
        }
        return this;
    }

    public Task<T> addOnFailureListener(OnFailureListener listener) {
        if (failure != null) {
            listener.onFailure(failure);
        }
        return this;
    }
}
