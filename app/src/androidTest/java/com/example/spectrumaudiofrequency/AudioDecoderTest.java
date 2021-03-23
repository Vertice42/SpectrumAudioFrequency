package com.example.spectrumaudiofrequency;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.mediaDecoder.AudioDecoder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class AudioDecoderTest {

    private final AudioDecoder audioDecoder;

    public AudioDecoderTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        audioDecoder = new AudioDecoder(context, R.raw.hollow);
        audioDecoder.prepare().join();

        try {
            Thread.sleep(60);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void addRequest() throws InterruptedException {
        final CountDownLatch signal = new CountDownLatch(1);

        int RequestsNumber = 20;

        boolean[] TestResult = new boolean[RequestsNumber];
        AtomicInteger ResponsesNumber = new AtomicInteger();

        for (int i = 0; i < RequestsNumber; i++) {
            int finalI = i;
            final int Time = i * audioDecoder.SampleDuration;
            audioDecoder.addRequest(new AudioDecoder.PeriodRequest(Time,
                    decoderResult -> {
                        TestResult[finalI] = (decoderResult.BytesSamplesChannels.length > 0
                                && Time == decoderResult.SampleTime);

                        decoderResult.getSampleChannels(audioDecoder);

                        if (!TestResult[finalI])
                            Log.e("BytesSamplesChannels " + finalI, "SampleTime: "
                                    + decoderResult.SampleTime + " =? " + Time
                                    + " BytesSamplesChannels.length =" +
                                    decoderResult.BytesSamplesChannels.length);

                        ResponsesNumber.getAndIncrement();
                        if (ResponsesNumber.get() >= RequestsNumber) signal.countDown();
                    }));
        }

        signal.await();

        for (boolean result : TestResult)
            if (!result) {
                Log.e("TestResult", Arrays.toString(TestResult));
                fail();
            }
    }

    @After
    public void clear() {
        audioDecoder.clear();
    }
}