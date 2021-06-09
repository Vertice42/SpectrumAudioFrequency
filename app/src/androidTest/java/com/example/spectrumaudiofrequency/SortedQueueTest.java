package com.example.spectrumaudiofrequency;

import android.media.MediaCodec;
import android.util.Log;

import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.CodecSample;
import com.example.spectrumaudiofrequency.core.codec_manager.SortedQueue;

import org.junit.Test;

import java.util.Random;

public class SortedQueueTest {
    private final SortedQueue sortedQueueTest;

    public SortedQueueTest() {
        sortedQueueTest = new SortedQueue();
    }

    @Test
    public void IsSorted() {
        Random random = new Random();
        int bound = 20;
        for (int i = 0; i < bound; i++) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.presentationTimeUs = random.nextInt(bound);
            CodecSample codecSample = new CodecSample(bufferInfo, new byte[0]);
            sortedQueueTest.add(codecSample);
        }

        Log.i("bound", sortedQueueTest.toString());
        //Assert.assertTrue();
    }
}
