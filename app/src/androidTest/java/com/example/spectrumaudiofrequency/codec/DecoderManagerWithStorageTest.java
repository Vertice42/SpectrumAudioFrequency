package com.example.spectrumaudiofrequency.codec;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.R;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.PeriodRequest;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManagerWithStorage;
import com.example.spectrumaudiofrequency.util.CalculatePerformance;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

@RunWith(AndroidJUnit4.class)
public class DecoderManagerWithStorageTest {
    private static final long MAX_TIME_OUT = 50000;
    private final ForkJoinPool forkJoinPool;
    private final DecoderManagerWithStorage decoderManagerWithStorage;
    ArrayList<TestResult> testResults = new ArrayList<>();
    boolean IsFinish = false;
    private boolean TimeOutPass = false;

    public DecoderManagerWithStorageTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        int id = R.raw.choose;
        decoderManagerWithStorage = new DecoderManagerWithStorage(context, id);
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
            else countDownLatch.countDown();
            TimeOutPass = false;
        });
    }

    private String verifyErrorsInDecodeResult(DecoderManager.DecoderResult decoderResult) {
        String message = "";
        if (decoderResult.bufferInfo == null) return message;
        if (decoderResult.bytes.length == 0) {
            message += " Sample length Error == 0";
        }
        if (AlreadyDecoded(testResults, decoderResult.bufferInfo.presentationTimeUs)) {
            message += " Sample AlreadyDecoded " + decoderResult.bufferInfo.presentationTimeUs;
        }
        return message;
    }

    private boolean AlreadyDecoded(ArrayList<TestResult> testResults, long SampleTime) {
        for (int i = 0; i < testResults.size(); i++) {
            TestResult testResult = testResults.get(i);
            if (testResult.SampleTime == SampleTime) return true;
            else if (testResult.SampleTime > SampleTime) return false;
        }
        return false;
    }

    @Before
    @Test
    public void addRequests() throws InterruptedException {
        CountDownLatch wantingResultOfRequest = new CountDownLatch(1);
        decoderManagerWithStorage.setNewSampleDuration(25000);

        int numberOfSamples = decoderManagerWithStorage.getNumberOfSamples();
        for (int i = 0; i < numberOfSamples; i++) {
            decoderManagerWithStorage.addRequest(new PeriodRequest(i, decoderResult -> {
                long presentationTimeUs = decoderResult.bufferInfo.presentationTimeUs;

                CalculatePerformance.LogPercentage("DecoderProgress",
                        presentationTimeUs,
                        decoderManagerWithStorage.getTrueMediaDuration());

                String message = verifyErrorsInDecodeResult(decoderResult);

                TimeOutPass = true;
                testResults.add(new TestResult(!message.equals(""), presentationTimeUs, message));
            }));
        }

        decoderManagerWithStorage.addOnFinishListener(() -> {
            decoderManagerWithStorage.addRequest(new PeriodRequest((numberOfSamples),
                    decoderResult -> wantingResultOfRequest.countDown()));
        });

        decoderManagerWithStorage.startDecoding();

        CountTimeout(wantingResultOfRequest);

        wantingResultOfRequest.await();
        Assert.assertTrue(testResults.size() > 0);

        for (int i = 0; i < testResults.size(); i++) {
            TestResult testResult = testResults.get(i);
            if (testResult.IsError) {
                Log.e("DecoderTestError", "testResults: " + Arrays.toString(testResults.toArray()));
                Log.e("DecoderTestError", testResult.Message);
                Assert.fail();
            }
        }
    }

    @After
    @Test
    public void addRequestsAfterDecodingEnd() throws InterruptedException {
        CalculatePerformance performance = new CalculatePerformance("RequestTime");
        performance.start();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final DecoderManager.DecoderResult[] Result = new DecoderManager.DecoderResult[1];

        decoderManagerWithStorage.addRequest(new PeriodRequest(0, decoderResult -> {
            Result[0] = decoderResult;
            countDownLatch.countDown();
        }));

        countDownLatch.await();
        performance.stop().logPerformance();
        Assert.assertNotNull(Result[0].bufferInfo);
        Assert.assertTrue(Result[0].bytes.length > 0);
    }

    public void removeOutputListener() {
        DecoderManager.OnDecodedListener onDecodedListener = codecSample ->
                Log.e("removeOutputListenerError", "lambda should not be called: ");
        decoderManagerWithStorage.addOnDecodeListener(onDecodedListener);
        decoderManagerWithStorage.removeOnDecodeListener(onDecodedListener);
        Assert.assertEquals(0, decoderManagerWithStorage.getDecodeListenersListSize());
    }

    @After
    public void clear() {
        decoderManagerWithStorage.clear();
    }
}
