package com.example.spectrumaudiofrequency;

import com.example.spectrumaudiofrequency.core.ByteQueue;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class ByteQueueTest {
    @Test
    public void addAndPollTest() {
        int addLength = 4;
        int removeLength = 2;

        byte[] input = new byte[addLength];
        for (int i = 0; i < addLength; i++) input[i] = (byte) (i + 1);

        ByteQueue byteQueue = new ByteQueue(1024 * 50);

        byteQueue.put(input);
        byteQueue.pollList(removeLength);
        byteQueue.put(input);

        int SizePosAdd = byteQueue.size();

        byte[] results = byteQueue.pollList(byteQueue.size());

        System.out.println("result: " + Arrays.toString(results));
        int ExpectedSizePosAdd = addLength - removeLength + addLength;
        Assert.assertEquals(ExpectedSizePosAdd, SizePosAdd);
    }
}