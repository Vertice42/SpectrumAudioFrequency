package com.example.spectrumaudiofrequency;

import com.example.spectrumaudiofrequency.core.ByteQueue;
import com.example.spectrumaudiofrequency.core.ByteQueueDynamic;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedList;

public class ByteQueueTest {
    @Test
    public void addAndPollTest() {
        int addLength = 8;
        int removeLength = 5;

        byte[] input = new byte[addLength];
        for (int i = 0; i < addLength; i++) input[i] = (byte) (i + 1);

        ByteQueue byteQueue = new ByteQueue(1024 * 50);
        LinkedList<Byte> resultExpected = new LinkedList<>();

        byteQueue.put(input);
        for (int i = 0; i < input.length; i++) resultExpected.add(input[i]);
        byteQueue.pollList(removeLength);

        for (int i = 0; i < removeLength; i++) resultExpected.pollFirst();

        int SizePosAdd = byteQueue.getSize();

        byte[] results = byteQueue.pollList(byteQueue.getSize());

        System.out.println("result: " + Arrays.toString(results));
        Assert.assertEquals(resultExpected.size(), SizePosAdd);

        byte[] expected = new byte[resultExpected.size()];
        for (int i = 0; i < expected.length; i++) expected[i] = resultExpected.pollFirst();

        Assert.assertArrayEquals(expected, results);
    }

    @Test
    public void ByteQueueDynamicTest() {
        int addLength = 8;
        int removeLength = 5;

        byte[] input = new byte[addLength];
        for (int i = 0; i < addLength; i++) input[i] = (byte) (i + 1);

        ByteQueueDynamic byteQueue = new ByteQueueDynamic(2);
        LinkedList<Byte> resultExpected = new LinkedList<>();

        byteQueue.put(input);
        for (byte value : input) resultExpected.add(value);

        byteQueue.pollList(removeLength);
        for (int i = 0; i < removeLength; i++) resultExpected.pollFirst();

        byteQueue.put(input);
        for (byte b : input) resultExpected.add(b);

        int SizePosAdd = byteQueue.getSize();
        byte[] results = byteQueue.pollList(byteQueue.getSize());

        System.out.println("result: " + Arrays.toString(results));
        Assert.assertEquals(resultExpected.size(), SizePosAdd);

        byte[] expected = new byte[resultExpected.size()];
        for (int i = 0; i < expected.length; i++) expected[i] = resultExpected.pollFirst();

        Assert.assertArrayEquals(expected, results);
    }

}