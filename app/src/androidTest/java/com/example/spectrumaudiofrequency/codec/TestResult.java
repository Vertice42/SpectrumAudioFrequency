package com.example.spectrumaudiofrequency.codec;

import org.jetbrains.annotations.NotNull;

class TestResult {
    boolean IsError;
    String Message;

    public TestResult(boolean isError, String message) {
        IsError = isError;
        Message = message;
    }

    @Override
    public @NotNull String toString() {
        return (IsError) ? "ERROR " : "" + Message;
    }
}
