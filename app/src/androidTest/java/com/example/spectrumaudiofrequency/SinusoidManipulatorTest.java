package com.example.spectrumaudiofrequency;

import com.example.spectrumaudiofrequency.sinusoid_manipulador.SinusoidManipulator;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class SinusoidManipulatorTest {
    @Test
    public void mixTest() {
        short[][][] inputTest = new short[2][2][1152];
        for (short[][] shorts : inputTest)
            for (short[] aShort : shorts) Arrays.fill(aShort, (short) 2);

        short[][] expectedResult = new short[2][1152];
        for (short[] shorts : expectedResult) Arrays.fill(shorts, (short) 2);

        short[][] mixResult = SinusoidManipulator.mix(inputTest);

        Assert.assertArrayEquals(expectedResult, mixResult);
    }
}
