package com.example.spectrumaudiofrequency.sinusoid_converter;

public class Converter {
    public static float ToLogarithmicScale(float data) {
        if (data == 0) return 0;
        return (float) (((data < 0) ? Math.log10(data * -1) * -1 : (Math.log10(data))) * 100);
    }
}
