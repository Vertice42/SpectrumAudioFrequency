package com.example.spectrumaudiofrequency.codec;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.R;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderCodecManager;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderCodecManager.PeriodRequest;
import com.example.spectrumaudiofrequency.core.codec_manager.media_decoder.AudioDecoder;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class DecoderCodecManagerTest {
    private final DecoderCodecManager decoderCodecManager;

    public DecoderCodecManagerTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        int id = R.raw.choose;
        decoderCodecManager = new DecoderCodecManager(context, id);

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
            final int Time = i * decoderCodecManager.SampleDuration;
            decoderCodecManager.addRequest(new PeriodRequest(Time,
                    decoderResult -> {
                        TestResult[finalI] = (decoderResult.BytesSamplesChannels.length > 0
                                && Time == decoderResult.SampleTime);

                        decoderResult.getSampleChannels(decoderCodecManager);

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

    /**
     * Waiting for processing to finish
     */
    @After
    public void clear() throws InterruptedException {
        final CountDownLatch signal = new CountDownLatch(1);
        decoderCodecManager.addRequest(new PeriodRequest(
                decoderCodecManager.MediaDuration - decoderCodecManager.SampleDuration,
                decoderResult -> {
                    decoderCodecManager.clear();
                    signal.countDown();
                }));
        signal.await();
    }
}
