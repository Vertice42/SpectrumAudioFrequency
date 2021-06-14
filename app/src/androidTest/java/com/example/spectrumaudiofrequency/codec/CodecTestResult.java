package com.example.spectrumaudiofrequency.codec;

import org.jetbrains.annotations.NotNull;

public class CodecTestResult {
    public long SampleTime;
    public int Size;
    public int flags;

    public CodecTestResult(long sampleTime, int size, int flags) {
        SampleTime = sampleTime;
        Size = size;
        this.flags = flags;
    }

    @Override
    public @NotNull String toString() {
        return "CodecTestResult{" +
                "SampleTime=" + SampleTime +
                ", Size=" + Size +
                ", flags=" + flags +
                '}';
    }
}
