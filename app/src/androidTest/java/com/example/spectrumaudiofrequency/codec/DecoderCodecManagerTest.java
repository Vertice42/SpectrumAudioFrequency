package com.example.spectrumaudiofrequency.codec;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.R;
import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager;
import com.example.spectrumaudiofrequency.util.PerformanceCalculator;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

import static com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.DecoderResult.SampleChannelsToBytes;
import static com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.DecoderResult.separateSampleChannels;

@RunWith(AndroidJUnit4.class)
public class DecoderCodecManagerTest {
    private static final long MAX_TIME_OUT = 50000;
    final int id = R.raw.choose;
    private final ForkJoinPool forkJoinPool;
    private final DecoderManager decoder;
    private final boolean TimeOutEnable = false;
    private final DecoderManager decodeWithRearrangement;
    private boolean TimeOutPass = false;

    public DecoderCodecManagerTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        decoder = new DecoderManager(context, id, sampleMetrics -> sampleMetrics);
        decodeWithRearrangement = new DecoderManager(context, id, metrics ->
                new CodecManager.SampleMetrics(metrics.SampleDuration,
                        (int) Math.ceil(((double)
                                metrics.SampleSize * metrics.SampleDuration) / metrics.SampleDuration)));

        forkJoinPool = ForkJoinPool.commonPool();
    }

    void CountTimeout(CountDownLatch countDownLatch) {
        forkJoinPool.execute(() -> {
            try {
                Thread.sleep(MAX_TIME_OUT);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (TimeOutPass) CountTimeout(countDownLatch);
            else {
                Log.e("countDownLatch", "Time Limit ");
                if (TimeOutEnable) countDownLatch.countDown();
            }
            TimeOutPass = false;
        });
    }

    private boolean checkIfItIsAlreadyDecoded(ArrayList<TestResult> testResults, long SampleTime) {
        for (int i = 0; i < testResults.size(); i++) {
            TestResult testResult = testResults.get(i);
            if (testResult != null)
                if (testResult.SampleTime == SampleTime) return true;
                else if (testResult.SampleTime > SampleTime) return false;
        }
        return false;
    }

    private String checkIfNotInSequence(ArrayList<TestResult> testResults) {
        StringBuilder r = new StringBuilder();
        long lastSampleTime = 0;
        for (int i = 0; i < testResults.size(); i++) {
            TestResult testResult = testResults.get(i);
            if (testResult.SampleTime < lastSampleTime)
                r.append(testResult.SampleTime).append(" | ");
            lastSampleTime = testResult.SampleTime;
        }
        return r.toString();
    }

    private void TestDecoding(DecoderManager decoder) throws InterruptedException {
        ArrayList<TestResult> testResults = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(1);

        PerformanceCalculator performanceCalculator = new PerformanceCalculator("Decoder");

        decoder.addOnDecodingListener(decoderResult -> {
            long presentationTimeUs = decoderResult.bufferInfo.presentationTimeUs;

            performanceCalculator.stop(presentationTimeUs, ((long) decoder.getTrueMediaDuration()))
                    .logPerformance();
            performanceCalculator.start();

            boolean IsError = false;
            String message = "";
            if (decoderResult.bytes.length == 0) {
                IsError = true;
                message += " Sample length Error == 0";
            }
            if (checkIfItIsAlreadyDecoded(testResults, presentationTimeUs)) {
                IsError = true;
                message += " Sample AlreadyDecoded " + presentationTimeUs;
            }

            TimeOutPass = true;
            testResults.add(new TestResult(IsError, presentationTimeUs, message));
        });

        decoder.addOnDecoderFinishListener(countDownLatch::countDown);

        CountTimeout(countDownLatch);
        decoder.start();
        countDownLatch.await();
        Assert.assertEquals("", checkIfNotInSequence(testResults));
        Assert.assertEquals(decoder.getNumberOfSamples(), testResults.size());
        for (int i = 0; i < testResults.size(); i++) {
            TestResult testResult = testResults.get(i);
            if (testResult.IsError) {
                Log.wtf("DecoderTestError", testResult.Message);
                Assert.assertFalse(testResult.IsError);
            }
        }
    }

    @Test
    public void decode() throws InterruptedException {
        TestDecoding(decoder);
    }

    @Test
    public void decodeWithRearrangement() throws InterruptedException {
        TestDecoding(decodeWithRearrangement);
    }

    @Test
    public void separateAndJoiningSampleChannelsTest() {
        byte[] original = new byte[16];
        int ChannelsNumber = 2;
        Random random = new Random();
        random.nextBytes(original);

        short[][] separateSample = separateSampleChannels(original, ChannelsNumber);
        byte[] unitedSample = SampleChannelsToBytes(separateSample, ChannelsNumber);

        Log.i("byte[] original", Arrays.toString(original));
        Log.i("separate", Arrays.deepToString(separateSample));
        Log.i("united__", Arrays.toString(unitedSample));

        Assert.assertArrayEquals(original, unitedSample);

    }
}
