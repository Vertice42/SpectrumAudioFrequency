package com.example.spectrumaudiofrequency.codec;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.R;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.DecoderResult;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.PeriodRequest;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManagerWithStorage;
import com.example.spectrumaudiofrequency.util.PerformanceCalculator;
import com.example.spectrumaudiofrequency.util.VerifyTimeOut;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;

@RunWith(AndroidJUnit4.class)
public class DecoderManagerWithStorageTest {
    private static final long MAX_TIME = 5000;
    private final static int id = R.raw.choose;
    private final DecoderManagerWithStorage decoder;
    LinkedList<TestResult> testResults = new LinkedList<>();

    public DecoderManagerWithStorageTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        decoder = new DecoderManagerWithStorage(context, id, sampleMetrics -> sampleMetrics);
    }

    private String verifyErrorsInDecodeResult(DecoderResult decoderResult) {
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

    private boolean AlreadyDecoded(LinkedList<TestResult> testResults, long SampleTime) {
        for (int i = 0; i < testResults.size(); i++) {
            TestResult testResult = testResults.get(i);
            if (testResult.SampleTime == SampleTime) return true;
            else if (testResult.SampleTime > SampleTime) return false;
        }
        return false;
    }

    private void VerifyErrors(DecoderResult decoderResult,
                              PerformanceCalculator performanceCalculator,
                              VerifyTimeOut verifyTimeOut) {
        verifyTimeOut.Pass();
        String message = verifyErrorsInDecodeResult(decoderResult);
        long presentationTimeUs = -1;
        boolean IsError = !message.equals("");

        if (decoderResult.bufferInfo != null) {
            performanceCalculator.stop(decoderResult.bufferInfo.presentationTimeUs,
                    (long) decoder.getTrueMediaDuration()).logPerformance();
            performanceCalculator.start();
            presentationTimeUs = decoderResult.bufferInfo.presentationTimeUs;
            message += " time " + presentationTimeUs;
        } else {
            message = "null";
        }
        testResults.add(new TestResult(IsError, presentationTimeUs, message));
    }

    public void makeRequests(int offset, int length,
                             PerformanceCalculator performanceCalculator,
                             VerifyTimeOut verifyTimeOut) {
        for (int i = offset; i < length; i++) {
            decoder.makeRequest(new PeriodRequest(i, decoderResult ->
                    VerifyErrors(decoderResult,
                            performanceCalculator,
                            verifyTimeOut)));
        }
    }

    @Test
    public void addRequestsTest() throws InterruptedException {
        CountDownLatch wantingResults = new CountDownLatch(1);
        int numberOfSamples = decoder.getNumberOfSamples();
        Assert.assertTrue(numberOfSamples > 0);

        VerifyTimeOut verifyTimeOut = new VerifyTimeOut(this.getClass(),
                MAX_TIME,
                wantingResults,
                false);

        if (decoder.IsDecoded()) {
            decoder.clear();
            Assert.fail();
        }
        PerformanceCalculator performanceCalculator;
        performanceCalculator = new PerformanceCalculator(("DecoderWithStorage"));

        makeRequests(0, numberOfSamples - 1,
                performanceCalculator,
                verifyTimeOut);

        decoder.addOnDecoderFinishListener(() -> {
            int TrueNumberOfSamples = decoder.getNumberOfSamples();
            int lastSample = TrueNumberOfSamples - 1;
            //if it is necessary to make more requests
            if (TrueNumberOfSamples > numberOfSamples) {
                int additionalRequests = TrueNumberOfSamples - numberOfSamples;
                makeRequests(numberOfSamples - additionalRequests,
                        lastSample,
                        performanceCalculator,
                        verifyTimeOut);
            } else if (TrueNumberOfSamples < numberOfSamples) lastSample++;

            decoder.makeRequest(new PeriodRequest(lastSample, lastResult -> {
                VerifyErrors(lastResult, performanceCalculator, verifyTimeOut);
                wantingResults.countDown();
            }));
        });

        wantingResults.await();
        int TrueNumberOfSamples = decoder.getNumberOfSamples();
        decoder.clear();
        decoder.close();

        Assert.assertTrue((testResults.size() >= TrueNumberOfSamples));
        for (int i = 0; i < testResults.size(); i++) {
            TestResult testResult = testResults.get(i);
            if (testResult.IsError) {
                Log.e("DecoderTestError", testResult.Message);
                Log.e("Results", testResults.toString());
                Assert.fail();
            }
        }
    }
}
