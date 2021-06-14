package com.example.spectrumaudiofrequency.core;

import android.util.Log;

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
        Log.i("ByteQueueDynamic", "expand Capacity to " + newCapacity + " bytes");
        byte[] temp = pollList(getSize());
        this.mainBuffer = ByteBuffer.allocate(newCapacity);
        this.leftoversBuffer = ByteBuffer.allocate(newCapacity);
        this.put(temp);
    }

    public void put(byte[] bytes) {
        int newSize = this.getSize() + bytes.length;
        int limit = this.remaining();
        if (newSize > limit) expandCapacity(newSize);
        super.put(bytes);
    }
}