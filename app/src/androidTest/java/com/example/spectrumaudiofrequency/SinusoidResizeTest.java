package com.example.spectrumaudiofrequency;

import android.util.Log;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

import static com.example.spectrumaudiofrequency.sinusoid_manipulador.SinusoidResize.resize;

public class SinusoidResizeTest {
    public void simplifySinusoidTest(int size, int newSize) {
        short value = 500;

        short[] sample = new short[size];
        Arrays.fill(sample, value);

        short[] expectedResult = new short[newSize];
        Arrays.fill(expectedResult, value);

        short[] result = resize(sample, expectedResult.length);

        Log.i("result", Arrays.toString(result));
        Assert.assertArrayEquals(result, expectedResult);

    }

    @Test
    public void resize_4To3() {
        simplifySinusoidTest(4, 3);
    }

    @Test
    public void resize_10To5() {
        simplifySinusoidTest(10, 5);
    }

    @Test
    public void resize_10To4() {
        simplifySinusoidTest(10, 4);
    }

    @Test
    public void resize_8To4() {
        simplifySinusoidTest(8, 4);
    }

    @Test
    public void resize_4To8() {
        simplifySinusoidTest(4, 8);
    }

    @Test
    public void resize_4To5() {
        simplifySinusoidTest(4, 5);
    }
}
