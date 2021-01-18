package com.example.spectrumaudiofrequency;

import android.util.Log;

import java.lang.reflect.Array;

public class Util {
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
}
