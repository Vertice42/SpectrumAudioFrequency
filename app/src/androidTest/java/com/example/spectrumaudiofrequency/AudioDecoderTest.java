package com.example.spectrumaudiofrequency;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.mediaDecoder.AudioDecoder;

import org.junit.After;
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
        audioDecoder = new AudioDecoder(context, R.raw.hollow, true);
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

        int RequestNumber = 10;

        boolean[] TestResult = new boolean[RequestNumber];
        AtomicInteger ResponsesNumber = new AtomicInteger();

        for (int i = 0; i < RequestNumber; i++) {
            int finalI = i;

            long Time = Math.abs(new Random()
                    .nextInt((int)(audioDecoder.MediaDuration + audioDecoder.SampleDuration)
                            / audioDecoder.SampleDuration) * audioDecoder.SampleDuration);

            audioDecoder.addRequest(new AudioDecoder.PeriodRequest(Time,
                    decoderResult -> {
                        ResponsesNumber.getAndIncrement();
                        TestResult[finalI] = (decoderResult.BytesSamplesChannels.length > 0 && Time == decoderResult.SampleTime);
                        if (!TestResult[finalI])
                            Log.e("BytesSamplesChannels" + finalI, "SampleTime: "
                                    + decoderResult.SampleTime + " =? " + Time
                                    + "BytesSamplesChannels.length =" + decoderResult.BytesSamplesChannels.length);

                        if (ResponsesNumber.get() >= RequestNumber) signal.countDown();
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
        //audioDecoder.clear();
    }
}