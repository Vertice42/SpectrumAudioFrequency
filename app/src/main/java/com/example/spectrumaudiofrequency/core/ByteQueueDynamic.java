package com.example.spectrumaudiofrequency.core;

import java.nio.ByteBuffer;

public class ByteQueueDynamic extends ByteQueue {
    public ByteQueueDynamic() {
        super(500);
    }

    public ByteQueueDynamic(int capacityDefault) {
        super(capacityDefault);
    }

    public void freeMemory() {
        expandCapacity(getSize());
    }

    private void expandCapacity(int newCapacity) {
        byte[] temp = pollList(getSize());
        this.mainBuffer = ByteBuffer.allocate(newCapacity);
        this.leftoversBuffer = ByteBuffer.allocate(newCapacity);
        this.put(temp);
    }

    public int getCapacity() {
        return this.mainBuffer.capacity();
    }

    public void put(byte[] bytes) {
        int newSize = this.getSize() + bytes.length;
        int capacity = this.getCapacity();
        if (newSize > capacity) expandCapacity(newSize);
        super.put(bytes);
    }
}