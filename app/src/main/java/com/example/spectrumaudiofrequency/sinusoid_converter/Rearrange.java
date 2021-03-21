package com.example.spectrumaudiofrequency.sinusoid_converter;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class Rearrange {

    /**
     * Decrease the Sample by averaging the values.
     * If the new length is greater than or equal to the old one,
     * returns the sample without modification.
     */
    public static short[] SimplifySinusoid(short @NotNull [] Sample, int NewLength) {
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

    public static float[] SimplifySinusoid(float @NotNull [] Sample, int NewLength) {
        if (NewLength >= Sample.length) return Sample;

        float[] result = new float[NewLength];
        int simplificationLength = Sample.length / NewLength;

        for (int i = 0; i < result.length; i++) {
            float media = 0;

            for (int j = 0; j < simplificationLength; j++)
                media += Sample[i * simplificationLength + j];

            media /= simplificationLength;

            result[i] = media;
        }
        return result;
    }

    public static class SuperSimplifySinusoid {
        ArrayList<ArrayList<Short>> SinusoidChannelSimplify = new ArrayList<>();
        private final int NewSampleLength;

        public SuperSimplifySinusoid(int NewSampleLength) {
            this.NewSampleLength = NewSampleLength;
        }

        public short[][] getSinusoidChannelSimplify() {
            short[][] SinusoidChannels = new short[SinusoidChannelSimplify.size()][];

            for (int i = 0; i < SinusoidChannels.length; i++) {
                ArrayList<Short> list = SinusoidChannelSimplify.get(i);
                SinusoidChannels[i] = new short[list.size()];
                for (int j = 0; j < SinusoidChannels[i].length; j++) {
                    SinusoidChannels[i][j] = SinusoidChannelSimplify.get(i).get(j);
                }
            }
            return SinusoidChannels;
        }

        public void Simplify(short[][] SampleChannels) {
            for (int channel = 0; channel < SampleChannels.length; channel++) {
                SinusoidChannelSimplify.add(new ArrayList<>());
                int simplificationLength = SampleChannels[channel].length / NewSampleLength;
                short media = 0;
                for (int i = 0; i < NewSampleLength; i++) {
                    for (int j = 0; j < simplificationLength; j++)
                        media += SampleChannels[channel][i * simplificationLength + j];
                    media /= simplificationLength;
                    SinusoidChannelSimplify.get(channel).add(media);
                }
            }
        }
    }
}
