package com.example.spectrumaudiofrequency;

import android.util.Log;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

import static com.example.spectrumaudiofrequency.sinusoid_converter.SamplingResize.ResizeSampling;

public class SamplingResizeTest {
    public void simplifySinusoidTest(int size, int newSize) {
        short value = 500;

        short[] sample = new short[size];
        Arrays.fill(sample, value);

        short[] expectedResult = new short[newSize];
        Arrays.fill(expectedResult, value);

        short[] result = ResizeSampling(sample, expectedResult.length);

        Log.i("result", Arrays.toString(result));
        Assert.assertArrayEquals(result, expectedResult);

    }

    @Test
    public void resizeSinusoidSize4To3() {
        simplifySinusoidTest(4, 3);
    }

    @Test
    public void resizeSinusoidSize8To4() {
        simplifySinusoidTest(8, 4);
    }

    @Test
    public void resizeSinusoidSize8To5() {
        simplifySinusoidTest(8, 5);
    }

    @Test
    public void resizeSinusoidSize80To60() {
        simplifySinusoidTest(80, 60);
    }

    @Test
    public void resizeSinusoidSize1024To1000() {
        simplifySinusoidTest(1024, 1000);
    }

    @Test
    public void resizeSinusoidSize2000To1000() {
        simplifySinusoidTest(2000, 1000);
    }
}
