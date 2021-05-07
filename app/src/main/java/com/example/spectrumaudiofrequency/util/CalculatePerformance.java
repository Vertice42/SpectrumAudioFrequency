package com.example.spectrumaudiofrequency.util;

import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

public class CalculatePerformance {
    public static class Performance {
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

        public void logPerformance() {
            logPerformance("");
        }
    }

    public static Performance SomePerformances(String name, Performance[] performances) {
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

    public CalculatePerformance(String Name) {
        this.Name = Name;
    }

    public CalculatePerformance(String Name, int ResetCont) {
        this.Name = Name;
        this.ResetCont = ResetCont;
    }

    public void start() {
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

    public static void LogPercentage(String text, long now, long total) {
        float percentage = Math.CalculatePercentage(now, total);
        String percentage_formatted = new DecimalFormat("0.00").format(percentage);
        Log.d(text, percentage_formatted + "%");
    }
}
