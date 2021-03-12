package com.example.spectrumaudiofrequency;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
        } catch (Exception e) {
            //Default to return 1 core
            return 1;
        }
    }

    public static int getNumberOfCores() {
        if (Build.VERSION.SDK_INT >= 17) {
            return Runtime.getRuntime().availableProcessors();
        } else {
            return getNumCoresOldPhones();
        }
    }

    public static class CalculatePerformance {
        static class Performance {
            String Name;
            long ProcessingTimeNano;
            long MediaNano;

            Performance(String Name, long ProcessingTimeNano, long Media) {
                this.Name = Name;
                this.ProcessingTimeNano = ProcessingTimeNano;
                this.MediaNano = Media;
            }

            public float getProcessingTime() {
                return ProcessingTimeNano / 1000000f;
            }

            public float getMediaNano() {
                return MediaNano / 1000000f;
            }

            public String toString(String extra) {
                return Name + ": " + getProcessingTime() + "ms | Media: " + getMediaNano() + "ms | " + extra;
            }

            public @NotNull String toString() {
                return toString("");
            }

            void logPerformance(String extra) {
                Log.i(Name, toString(extra));
            }

            void logPerformance() {
                logPerformance("");
            }
        }

        static Performance SomePerformances(String name, Performance[] performances) {
            long MediaNano = 0;
            long ProcessingTimeNano = 0;
            for (Performance performance : performances) {
                MediaNano += performance.MediaNano;
                ProcessingTimeNano += performance.ProcessingTimeNano;
            }
            return new Performance(name, ProcessingTimeNano, MediaNano);
        }

        private final String Name;
        private int ResetCont = 100;
        private long Time;

        private long ProcessingTimeSome;
        private int count = 0;

        CalculatePerformance(String Name) {
            this.Name = Name;
        }

        CalculatePerformance(String Name, int ResetCont) {
            this.Name = Name;
            this.ResetCont = ResetCont;
        }

        void start() {
            Time = System.nanoTime();
        }

        public Performance stop() {
            if (count > ResetCont) {
                ProcessingTimeSome = 0;
                count = 0;
            }

            long ProcessingTime = System.nanoTime() - Time;
            ProcessingTimeSome += ProcessingTime;
            count++;

            long Media = ProcessingTimeSome / count;

            return new Performance(Name, ProcessingTime, Media);
        }
    }

    public static void SaveJsonFile(Context context, String FileName, String data) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter
                    (context.openFileOutput(FileName + ".json", Context.MODE_PRIVATE));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    public static String ReadJsonFile(Context context, String FileName) {

        String ret = "";

        try {
            InputStream inputStream = context.openFileInput(FileName + ".json");

            if (inputStream != null) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ((receiveString = bufferedReader.readLine()) != null) {
                    stringBuilder.append("\n").append(receiveString);
                }

                inputStream.close();
                ret = stringBuilder.toString();
            }
        } catch (FileNotFoundException e) {
            Log.e("ReadJsonFile", "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e("ReadJsonFile", "Can not read file: " + e.toString());
        }

        return ret;
    }

}
