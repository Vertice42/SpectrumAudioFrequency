package com.example.spectrumaudiofrequency;

import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Array;
import java.util.regex.Pattern;

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

        float[] c = new float[a.length+b.length];

        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);

        return c;
    }
    public static float[] ConcatenateArray(float[][] a) {
        int length = 0;

        for (float[] floats : a) length += floats.length;

        float[] b = new float[length];

        for (int i = 0; i < a.length; i++){
            System.arraycopy(a[i], 0, b, a[i].length * i, a[i].length);
        }

        return b;
    }


    public static float[] toFloat(short[] shorts){
        float[] floats = new float[shorts.length];
        for (int i = 0; i < shorts.length; i++) floats[i] = shorts[i];
        return floats;
    }
    private static int getNumCoresOldPhones() {
        //Private Class to display only CPU devices in the directory listing
        class CpuFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                //Check if filename is "cpu", followed by a single digit number
                return Pattern.matches("cpu[0-9]+", pathname.getName());
            }
        }

        try {
            //Get directory containing CPU info
            File dir = new File("/sys/devices/system/cpu/");
            //Filter to only list the devices we care about
            File[] files = dir.listFiles(new CpuFilter());
            //Return the number of cores (virtual CPU devices)
            return files.length;
        } catch(Exception e) {
            //Default to return 1 core
            return 1;
        }
    }
    public static int getNumberOfCores() {
        if(Build.VERSION.SDK_INT >= 17) {
            return Runtime.getRuntime().availableProcessors();
        }
        else {
            // Use saurabh64's answer
            return getNumCoresOldPhones();
        }
    }
}
