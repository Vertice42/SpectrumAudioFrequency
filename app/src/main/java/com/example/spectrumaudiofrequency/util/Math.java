package com.example.spectrumaudiofrequency.util;

public class Math {
    public static float CalculatePercentage(long now, long Total) {
        return (float) (((double) now / Total) * 100f);
    }
}
