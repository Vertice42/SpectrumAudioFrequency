package com.example.spectrumaudiofrequency.codec;

import org.jetbrains.annotations.NotNull;

class TestResult {
    boolean IsError;
    long SampleTime;
    String Message;

    public TestResult(boolean isError,long sampleTime, String message) {
        IsError = isError;
        SampleTime = sampleTime;
        Message = message;
    }

    @Override
    public @NotNull String toString() {
        return (IsError) ? "ERROR " : "" + Message;
    }
}
