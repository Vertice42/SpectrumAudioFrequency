package com.example.spectrumaudiofrequency.sinusoid_converter;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class SamplingResize {

    /**
     * Decrease the Sample by averaging the values.
     * If the new length is greater than or equal to the old one,
     * returns the sample without modification.
     */

    private static short[] resizeBilinear(short @NotNull [] Sample, int NewLength) {
        if (NewLength >= Sample.length) return Sample;

        short[] result = new short[NewLength];
        int divider = Sample.length / NewLength;

        for (int i = 0; i < result.length; i++) {
            short media = 0;
            for (int j = 0; j < divider; j++) media += Sample[i * divider + j];
            media /= divider;

            result[i] = media;
        }
        return result;
    }

    private static short[] resizeNotBilinear(short @NotNull [] Sample, int NewLength) {
        if (NewLength >= Sample.length) return Sample;

        short[] result = new short[NewLength];

        for (int i = result.length - 1; i > 0; i--) {
            result[i] = (short) ((Sample[i] * NewLength / (double) Sample.length) + Sample[i - 1] * (Sample.length % NewLength) / (double) Sample.length);
        }
        return result;
    }

    public static short[] ResizeSample(short @NotNull [] Sample, int NewLength) {
        if (Sample.length % NewLength == 0) return resizeBilinear(Sample, NewLength);
        return resizeNotBilinear(Sample, NewLength);
    }

    public static short[][] ResizeSamplesChannels(short @NotNull [][] SampleChannels, int NewLength) {
        short[][] ResizedSamples = new short[SampleChannels.length][];
        for (int i = 0; i < SampleChannels.length; i++) {
            ResizedSamples[i] = ResizeSample(SampleChannels[i], NewLength);
        }
        return ResizedSamples;
    }

    public static class SuperResize {
        private final int NewSampleLength;
        ArrayList<ArrayList<Short>> SinusoidChannels = new ArrayList<>();

        public SuperResize(int NewSampleLength) {
            this.NewSampleLength = NewSampleLength;
        }

        public short[][] getSinusoidChannels() {
            short[][] SinusoidChannels = new short[this.SinusoidChannels.size()][];

            for (int i = 0; i < SinusoidChannels.length; i++) {
                ArrayList<Short> list = this.SinusoidChannels.get(i);
                SinusoidChannels[i] = new short[list.size()];
                for (int j = 0; j < SinusoidChannels[i].length; j++) {
                    SinusoidChannels[i][j] = this.SinusoidChannels.get(i).get(j);
                }
            }
            return SinusoidChannels;
        }

        public void resize(short[][] SampleChannels) {
            for (int channel = 0; channel < SampleChannels.length; channel++) {
                SinusoidChannels.add(new ArrayList<>());
                int simplificationLength = SampleChannels[channel].length / NewSampleLength;
                short media = 0;
                for (int i = 0; i < NewSampleLength; i++) {
                    for (int j = 0; j < simplificationLength; j++)
                        media += SampleChannels[channel][i * simplificationLength + j];
                    media /= simplificationLength;
                    SinusoidChannels.get(channel).add(media);
                }
            }
        }
    }
}
