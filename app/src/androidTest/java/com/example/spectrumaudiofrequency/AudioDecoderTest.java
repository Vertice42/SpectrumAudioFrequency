package com.example.spectrumaudiofrequency;

import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.MediaDecoder.AudioDecoder;
import com.example.spectrumaudiofrequency.MediaDecoder.dbAudioDecoder;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.fail;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class AudioDecoderTest {

    private final AudioDecoder audioDecoder;
    private final Context context;

    public AudioDecoderTest() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String pkgName = context.getPackageName();
        int id = R.raw.choose;
        Uri uri = Uri.parse("android.resource://" + pkgName + "/raw/" + id);

        audioDecoder = new AudioDecoder(context, uri);
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

        int RequestNumber = 500;

        boolean[] TestResult = new boolean[RequestNumber];
        AtomicInteger ResponsesNumber = new AtomicInteger();

        for (int i = 0; i < RequestNumber; i++) {
            int finalI = i;

            long Time = Math.abs(new Random().nextInt((int)
                    (audioDecoder.MediaDuration / audioDecoder.SampleDuration))
                    * audioDecoder.SampleDuration);

            audioDecoder.addRequest(new AudioDecoder.PeriodRequest(Time, audioDecoder.SampleDuration,
                    decoderResult -> {
                        ResponsesNumber.getAndIncrement();
                        TestResult[finalI] = (decoderResult.SamplesChannels[0].length > 100);

                        if (ResponsesNumber.get() >= RequestNumber) signal.countDown();
                    }));
        }

        signal.await();

        for (boolean result : TestResult) if (!result) fail();
    }
}