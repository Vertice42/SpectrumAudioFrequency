package com.example.spectrumaudiofrequency.codec;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.R;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class DecoderCodeTest {
    private final ForkJoinPool forkJoinPool;
    private final DecoderManager decoderManager;
    private static final long MAX_TIME_OUT = 500;

    public DecoderCodeTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        int id = R.raw.choose;
        decoderManager = new DecoderManager(context, id);

        forkJoinPool = ForkJoinPool.commonPool();
    }

    private boolean TimeOutPass = false;

    void CountTimeout(CountDownLatch countDownLatch) {
        forkJoinPool.execute(() -> {
            try {
                Thread.sleep(MAX_TIME_OUT);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (TimeOutPass) CountTimeout(countDownLatch);
            //else countDownLatch.countDown();
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

        decoderManager.addOnDecodeListener(decoderResult -> {
            long presentationTimeUs = decoderResult.bufferInfo.presentationTimeUs;
            double progress = ((double) presentationTimeUs /
                    decoderManager.TrueMediaDuration()) * 100;
            Log.i("DecoderProgress", progress + "%");
            boolean IsError = false;
            String message = "";
            if (decoderResult.Sample.length == 0) {
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

        decoderManager.addOnEndListener(() -> {
            Log.i("OnEndListener", "END");
            countDownLatch.countDown();
        });

        CountTimeout(countDownLatch);
        decoderManager.setNewSampleDuration(25000);
        decoderManager.startDecoding();

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
}
