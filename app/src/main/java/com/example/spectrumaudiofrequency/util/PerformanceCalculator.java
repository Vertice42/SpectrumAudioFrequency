package com.example.spectrumaudiofrequency.util;

import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

public class PerformanceCalculator {
    private final String Name;
    private final DecimalFormat decimalFormat;
    private int ResetCont = 5;
    private long Time;
    private long ProcessingTimeSome;
    private int count = 0;

    public PerformanceCalculator(String Name) {
        this.Name = Name;
        decimalFormat = new DecimalFormat("000.000");
    }

    public PerformanceCalculator(String Name, int ResetCont) {
        this.Name = Name;
        this.ResetCont = ResetCont;
        decimalFormat = new DecimalFormat("000.000");
    }

    public static Performance SomePerformances(PerformanceCalculator performanceCalculator,
                                               String name,
                                               Performance[] performances) {
        long MediaNano = 0;
        long ProcessingTimeNano = 0;
        for (Performance performance : performances) {
            MediaNano += performance.MediaNano;
            ProcessingTimeNano += performance.ProcessingTimeNano;
        }
        return new Performance(performanceCalculator.decimalFormat,
                name,
                ProcessingTimeNano,
                MediaNano);
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

        return new Performance(decimalFormat, Name, ProcessingTime, Media);
    }

    public Performance stop(long now, long total) {
        if (count > ResetCont) {
            ProcessingTimeSome = 0;
            count = 0;
        }

        float percentage = Math.CalculatePercentage(now, total);
        String progress = decimalFormat.format(percentage) + "% time:" + now + " duration:" + total;

        long ProcessingTime = System.nanoTime() - Time;
        ProcessingTimeSome += ProcessingTime;
        count++;

        long Media = ProcessingTimeSome / count;

        return new Performance(decimalFormat, Name, ProcessingTime, Media, progress);
    }

    public static class Performance {
        private final DecimalFormat decimalFormat;
        String Name;
        long ProcessingTimeNano;
        long MediaNano;
        private String Extra;

        Performance(DecimalFormat decimalFormat, String Name, long ProcessingTimeNano, long Media) {
            this.Name = Name;
            this.ProcessingTimeNano = ProcessingTimeNano;
            this.MediaNano = Media;
            this.decimalFormat = decimalFormat;
        }

        Performance(DecimalFormat decimalFormat,
                    String Name,
                    long ProcessingTimeNano,
                    long Media,
                    String Extra) {
            this.Name = Name;
            this.ProcessingTimeNano = ProcessingTimeNano;
            this.MediaNano = Media;
            this.Extra = Extra;
            this.decimalFormat = decimalFormat;


        }

        public String getProcessingTime() {
            return decimalFormat.format(ProcessingTimeNano / 1000000f);
        }

        public String getMediaNano() {
            return decimalFormat.format(MediaNano / 1000000f);
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
