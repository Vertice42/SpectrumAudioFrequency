package com.example.spectrumaudiofrequency.core;

import java.nio.ByteBuffer;

public class ByteQueue {
    private final ByteBuffer mainBuffer;
    private final ByteBuffer leftoversBuffer;

    public ByteQueue(int capacity) {
        this.mainBuffer = ByteBuffer.allocate(capacity);
        this.leftoversBuffer = ByteBuffer.allocate(capacity);

    }

    public int size() {
        if (mainBuffer.position() > 0) return mainBuffer.position();
        else return leftoversBuffer.position();
    }

    public void put(byte[] bytes) {
        if (leftoversBuffer.position() > 0) {
            leftoversBuffer.flip();
            mainBuffer.put(leftoversBuffer);
            leftoversBuffer.clear();
        }
        mainBuffer.put(bytes);
    }


    public byte[] pollList(int length) {
        byte[] bytes = new byte[length];
        if (leftoversBuffer.position() > 0) {

            leftoversBuffer.flip();
            leftoversBuffer.get(bytes);
            if (leftoversBuffer.remaining() > 0) mainBuffer.put(leftoversBuffer);
            leftoversBuffer.clear();

        } else {

            mainBuffer.flip();
            mainBuffer.get(bytes);
            if (mainBuffer.remaining() > 0) leftoversBuffer.put(mainBuffer);
            mainBuffer.clear();

        }

        return bytes;
    }
}