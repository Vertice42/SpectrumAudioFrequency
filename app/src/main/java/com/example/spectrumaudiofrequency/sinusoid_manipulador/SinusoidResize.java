package com.example.spectrumaudiofrequency.sinusoid_manipulador;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class SinusoidResize {

    /**
     * Decrease the Sinusoid by averaging the values.
     * If the new length is greater than or equal to the old one,
     * returns the Sinusoid without modification.
     */

    private static short[] resizeBilinear(short @NotNull [] Sinusoid, int NewLength) {
        if (NewLength >= Sinusoid.length) return Sinusoid;

        short[] result = new short[NewLength];
        int divider = Sinusoid.length / NewLength;

        for (int i = 0; i < result.length; i++) {
            short media = 0;
            for (int j = 0; j < divider; j++) media += Sinusoid[i * divider + j];
            media /= divider;

            result[i] = media;
        }
        return result;
    }

    private static short[] resizeNotBilinear(short @NotNull [] Sinusoid, int NewLength) {
        short[] resized = new short[NewLength];
        int dif = NewLength - Sinusoid.length;

        if (dif == 0) return Sinusoid;
        else if (dif > 0) {
            int space = Sinusoid.length / dif;
            int index = 0;
            int spaceCount = 0;
            int i = 0;
            while (i < NewLength) {
                if (spaceCount == space) {
                    if (index + 1 > Sinusoid.length - 1) {
                        resized[i] = (short) (Sinusoid[Sinusoid.length - 1]);
                    } else {
                        resized[i] = (short) ((Sinusoid[index] + Sinusoid[index + 1]) / 2);
                    }
                    spaceCount = 0;
                } else {
                    resized[i] = Sinusoid[index];
                    spaceCount++;
                    index++;
                }
                i++;
            }
            return resized;

        } else {
            int skip = Sinusoid.length / dif;
            int skipCount = 0;
            int i = 0;
            while (i < NewLength) {
                if (skipCount == skip) {
                    i++;
                    skipCount = 0;
                }
                resized[i] = Sinusoid[i];
                i++;
            }

            return resized;
        }
    }

    public static short[] resize(short @NotNull [] Sinusoid, int NewLength) {
        if (Sinusoid.length % NewLength == 0) return resizeBilinear(Sinusoid, NewLength);
        return resizeNotBilinear(Sinusoid, NewLength);
    }

    public static short[][] resize(short @NotNull [][] SinusoidChannels, int NewLength) {
        short[][] result = new short[SinusoidChannels.length][];
        for (int i = 0; i < SinusoidChannels.length; i++) {
            result[i] = resize(SinusoidChannels[i], NewLength);
        }
        return result;
    }

    public static class SuperResize {
        private final int NewSinusoidLength;
        ArrayList<ArrayList<Short>> SinusoidChannels = new ArrayList<>();

        public SuperResize(int NewSinusoidLength) {
            this.NewSinusoidLength = NewSinusoidLength;
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

        public void resize(short[][] Sinusoids) {
            for (int channel = 0; channel < Sinusoids.length; channel++) {
                this.SinusoidChannels.add(new ArrayList<>());
                int simplificationLength = Sinusoids[channel].length / NewSinusoidLength;
                short media = 0;
                for (int i = 0; i < NewSinusoidLength; i++) {
                    for (int j = 0; j < simplificationLength; j++)
                        media += Sinusoids[channel][i * simplificationLength + j];
                    media /= simplificationLength;
                    this.SinusoidChannels.get(channel).add(media);
                }
            }
        }
    }
}
