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
    private static final long MAX_TIME_OUT = 1000;

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
            else countDownLatch.countDown();
            TimeOutPass = false;
        });
    }

    @Test
    public void Decode() throws InterruptedException {
        ArrayList<TestResult> testResults = new ArrayList<>();
        decoderManager.addOnDecodeListener(decoderResult -> {
            boolean IsError = false;
            String message = "";
            if (decoderResult.Sample.length == 0) {
                IsError = true;
                message += " Sample length Error == 0";
            }
            TimeOutPass = true;
            testResults.add(new TestResult(IsError, message));
        });
        CountDownLatch countDownLatch = new CountDownLatch(1);
        decoderManager.addOnEndListener(countDownLatch::countDown);

        CountTimeout(countDownLatch);
        decoderManager.setNewSampleDuration(25000);
        decoderManager.startDecoding();

        countDownLatch.await();
        for (int i = 0; i < testResults.size(); i++) {
            TestResult testResult = testResults.get(i);
            if (testResult.IsError) {
                Log.wtf("DecoderTestError", testResult.Message);
                Assert.assertFalse(testResult.IsError);
            }
        }
    }
}
