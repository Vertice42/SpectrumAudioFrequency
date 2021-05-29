package com.example.spectrumaudiofrequency.util;

import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

public class CalculatePerformance {
    private final String Name;
    private int ResetCont = 5;
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

    public static Performance SomePerformances(String name, Performance[] performances) {
        long MediaNano = 0;
        long ProcessingTimeNano = 0;
        for (Performance performance : performances) {
            MediaNano += performance.MediaNano;
            ProcessingTimeNano += performance.ProcessingTimeNano;
        }
        return new Performance(name, ProcessingTimeNano, MediaNano);
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

    public Performance stop(long now, long total) {
        if (count > ResetCont) {
            ProcessingTimeSome = 0;
            count = 0;
        }

        float percentage = Math.CalculatePercentage(now, total);
        String percentage_formatted = new DecimalFormat("0.00").format(percentage);
        String progress = percentage_formatted + "% time:" + now + " duration:" + total;

        long ProcessingTime = System.nanoTime() - Time;
        ProcessingTimeSome += ProcessingTime;
        count++;

        long Media = ProcessingTimeSome / count;

        return new Performance(Name, ProcessingTime, Media, progress);
    }

    public static class Performance {
        String Name;
        long ProcessingTimeNano;
        long MediaNano;
        private String Extra;

        Performance(String Name, long ProcessingTimeNano, long Media) {
            this.Name = Name;
            this.ProcessingTimeNano = ProcessingTimeNano;
            this.MediaNano = Media;
        }

        Performance(String Name, long ProcessingTimeNano, long Media, String Extra) {
            this.Name = Name;
            this.ProcessingTimeNano = ProcessingTimeNano;
            this.MediaNano = Media;
            this.Extra = Extra;
        }

        public float getProcessingTime() {
            return ProcessingTimeNano / 1000000f;
        }

        public float getMediaNano() {
            return MediaNano / 1000000f;
        }

        public String toString(String extra) {
            return getProcessingTime() + "ms | Media: " + getMediaNano() + "ms | " + Extra + extra;
        }

        public @NotNull String toString() {
            return toString("");
        }

        public void logPerformance(String extra) {
            Log.i(Name, toString(extra));
        }

        public void logPerformance() {
            logPerformance("");
        }
    }
}
