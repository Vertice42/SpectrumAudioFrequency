package com.example.spectrumaudiofrequency.codec;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.R;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager;
import com.example.spectrumaudiofrequency.util.CalculatePerformance;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

import static com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.DecoderResult.joiningSampleChannels;
import static com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.DecoderResult.separateSampleChannels;

@RunWith(AndroidJUnit4.class)
public class DecoderCodecManagerTest {
    private static final long MAX_TIME_OUT = 50000;
    private final ForkJoinPool forkJoinPool;
    private final DecoderManager decoder;
    private boolean TimeOutPass = false;

    public DecoderCodecManagerTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        int id = R.raw.choose;
        decoder = new DecoderManager(context, id);
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
                countDownLatch.countDown();
            }
            TimeOutPass = false;
        });
    }

    private boolean AlreadyDecoded(ArrayList<TestResult> testResults, long SampleTime) {
        for (int i = 0; i < testResults.size(); i++) {
            TestResult testResult = testResults.get(i);
            if (testResult.SampleTime == SampleTime) return true;
            else if (testResult.SampleTime > SampleTime) return false;
        }
        return false;
    }

    @Test
    public void Decode() throws InterruptedException {
        ArrayList<TestResult> testResults = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(1);

        decoder.addOnDecodeListener(decoderResult -> {
            long presentationTimeUs = decoderResult.bufferInfo.presentationTimeUs;

            CalculatePerformance.LogPercentage("DecoderProgress",
                    presentationTimeUs,
                    decoder.getTrueMediaDuration());

            boolean IsError = false;
            String message = "";
            if (decoderResult.bytes.length == 0) {
                IsError = true;
                message += " Sample length Error == 0";
            }
            if (AlreadyDecoded(testResults, presentationTimeUs)) {
                IsError = true;
                message += " Sample AlreadyDecoded " + presentationTimeUs;
            }

            TimeOutPass = true;
            testResults.add(new TestResult(IsError, presentationTimeUs, message));
        });

        decoder.addOnFinishListener(() -> {
            Log.i("OnFinishListener", "Finish");
            countDownLatch.countDown();
        });

        CountTimeout(countDownLatch);
        decoder.setNewSampleDuration(25000);
        decoder.startDecoding();

        countDownLatch.await();
        Assert.assertTrue(testResults.size() > 0);
        for (int i = 0; i < testResults.size(); i++) {
            TestResult testResult = testResults.get(i);
            if (testResult.IsError) {
                Log.wtf("DecoderTestError", testResult.Message);
                Assert.assertFalse(testResult.IsError);
            }
        }
    }

    public void removeOutputListener() {
        DecoderManager.OnDecodedListener onDecodedListener = codecSample ->
                Log.e("removeOutputListenerError", "lambda should not be called: ");
        decoder.addOnDecodeListener(onDecodedListener);
        decoder.removeOnDecodeListener(onDecodedListener);
        Assert.assertEquals(0, decoder.getDecodeListenersListSize());
    }

    @Test
    public void separateAndJoiningSampleChannelsTest() {
        byte[] original = new byte[16];
        int ChannelsNumber = 2;
        Random random = new Random();
        random.nextBytes(original);

        short[][] separateSample = separateSampleChannels(original, ChannelsNumber);
        byte[] unitedSample = joiningSampleChannels(separateSample, ChannelsNumber);

        Log.i("byte[] original", Arrays.toString(original));
        Log.i("separate", Arrays.deepToString(separateSample));
        Log.i("united__", Arrays.toString(unitedSample));

        Assert.assertArrayEquals(original, unitedSample);

    }
}
