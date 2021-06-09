package com.example.spectrumaudiofrequency;

import com.example.spectrumaudiofrequency.core.ByteQueue;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class ByteQueueTest {
    @Test
    public void addAndPollTest() {
        int addLength = 8;
        int removeLength = 5;

        byte[] input = new byte[addLength];
        for (int i = 0; i < addLength; i++) input[i] = (byte) (i + 1);

        ByteQueue byteQueue = new ByteQueue(1024 * 50);

        int ExpectedSizePosAdd = 0;

        byteQueue.put(input);
        ExpectedSizePosAdd += addLength;

        byteQueue.pollList(removeLength);
        ExpectedSizePosAdd -= removeLength;

        int SizePosAdd = byteQueue.getSize();

        byte[] results = byteQueue.pollList(byteQueue.getSize());

        System.out.println("result: " + Arrays.toString(results));
        Assert.assertEquals(ExpectedSizePosAdd, SizePosAdd);
    }
}