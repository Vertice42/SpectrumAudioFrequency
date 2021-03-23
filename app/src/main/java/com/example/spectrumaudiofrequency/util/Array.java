package com.example.spectrumaudiofrequency.util;

import android.util.Log;

import java.util.Arrays;

public class Array {
    public static short[][][] SplitArray(short[][] OriginalArray, int Divider) {
        int DataLength = (OriginalArray[0].length > 0) ? OriginalArray[0].length / Divider : 0;
        short[][][] WavePiecesArrays = new short[Divider][OriginalArray.length][DataLength];
        for (int b = 0; b < Divider; b++) {
            for (int a = 0; a < OriginalArray.length; a++) {
                int copy_start = (WavePiecesArrays[b][a].length > 0) ? (WavePiecesArrays[b][a].length * (b)) : 0;
                System.arraycopy(
                        OriginalArray[a], copy_start, WavePiecesArrays[b][a], 0, DataLength);
            }
        }
        return WavePiecesArrays;
        //todo possivel provlema com volume de dados inpares
    }

    public static short[][] SplitArray(short[] OriginalArray, int Divider) {
        int DataLength = (OriginalArray.length > 0) ? OriginalArray.length / Divider : 0;
        short[][] SpitedArray = new short[Divider][DataLength];
        for (int b = 0; b < Divider; b++) {
            int copy_start = (SpitedArray[b].length > 0) ? (SpitedArray[b].length * (b)) : 0;
            System.arraycopy(
                    OriginalArray, copy_start, SpitedArray[b], 0, DataLength);
        }
        return SpitedArray;
        //todo possivel provlema com volume de dados inpares
    }

    public static short[][] ConcatenateArray(short[][] a, short[][] b) {
        if (a.length == 0) return b;
        short[][] c = new short[a.length][a[0].length + b[0].length];

        for (int i = 0; i < a.length; i++) {
            c[i] = new short[a[i].length + b[i].length];

            System.arraycopy(a[i], 0, c[i], 0, a[i].length);
            System.arraycopy(b[i], 0, c[i], a[i].length, b[i].length);
        }

        return c;
    }

    public static float[][] ConcatenateArray(float[][] a, float[][] b) {
        if (a.length == 0) return b;
        float[][] c = new float[a.length][a[0].length + b[0].length];

        for (int i = 0; i < a.length; i++) {
            c[i] = new float[a[i].length + b[i].length];

            System.arraycopy(a[i], 0, c[i], 0, a[i].length);
            System.arraycopy(b[i], 0, c[i], a[i].length, b[i].length);
        }

        return c;
    }

    public static float[] ConcatenateArray(float[] a, float[] b) {
        if (a.length == 0) return b;

        float[] c = new float[a.length + b.length];

        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);

        return c;
    }

    public static double[] ConcatenateArray(double[][] a) {
        int length = 0;

        for (double[] floats : a) length += floats.length;

        double[] b = new double[length];

        for (int i = 0; i < a.length; i++) {
            System.arraycopy(a[i], 0, b, a[i].length * i, a[i].length);
        }

        return b;
    }

    public static float[] toFloat(short[] shorts) {
        float[] floats = new float[shorts.length];
        for (int i = 0; i < shorts.length; i++) floats[i] = shorts[i];
        return floats;
    }

    public static double[] toDouble(float[] floats) {
        double[] doubles = new double[floats.length];
        for (int i = 0; i < floats.length; i++) doubles[i] = (double) floats[i];
        return doubles;
    }

    public static float[] toFloat(double[] doubles) {
        float[] floats = new float[doubles.length];
        for (int i = 0; i < doubles.length; i++) floats[i] = (float) doubles[i];
        return floats;
    }

    public static float someArray(float[] floats) {
        float some = 0;
        for (float aFloat : floats) some += aFloat;
        return some;
    }

    public static float calculateEquity(float[] a, float[] b) {
        if (a.length != b.length) return -1;

        float[] differences = new float[a.length];

        for (int i = 0; i < a.length; i++)differences[i] = (b[i] - b[i]) / b[i] ;

        Log.i("differences", Arrays.toString(differences));

        return (someArray(differences) / differences.length) * 100;
    }
}
