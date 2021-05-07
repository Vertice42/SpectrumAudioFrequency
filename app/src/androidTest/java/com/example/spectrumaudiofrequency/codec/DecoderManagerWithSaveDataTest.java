package com.example.spectrumaudiofrequency.codec;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.R;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.PeriodRequest;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManagerWithSaveData;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class DecoderManagerWithSaveDataTest {
    private final ForkJoinPool forkJoinPool;
    private final DecoderManagerWithSaveData decoderCodecWithCacheManager;
    private static final long MAX_TIME_OUT = 1000;

    public DecoderManagerWithSaveDataTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        int id = R.raw.choose;
        decoderCodecWithCacheManager = new DecoderManagerWithSaveData(context, id);

        forkJoinPool = ForkJoinPool.commonPool();
    }

    private boolean AllNotNull(Object[] objects) {
        for (Object object : objects) if (object == null) return false;
        return true;
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
    public void addRequests() throws InterruptedException {
        final CountDownLatch signal = new CountDownLatch(1);
        decoderCodecWithCacheManager.setNewSampleDuration(25000);
        decoderCodecWithCacheManager.startDecoding();
        int RequestsNumber = decoderCodecWithCacheManager.getSampleLength();

        TestResult[] TestsResults = new TestResult[RequestsNumber];

        CountTimeout(signal);
        for (int i = 0; i < RequestsNumber; i++) {
            int SampleId = i;

            decoderCodecWithCacheManager.addRequest(new PeriodRequest(SampleId, decoderResult -> {
                boolean IsError = false;
                String Message = "{" + SampleId;
                if (!decoderResult.SampleTimeNotExist()) {
                    if (decoderResult.bytes.length <= 0) {
                        Message += "size 0";
                        IsError = true;
                    }
                    short[][] data = decoderResult.getSampleChannels(decoderCodecWithCacheManager);
                } else {
                    Message += " SampleTimeNotExist";
                }
                Message += " }";

                if (IsError) Log.e("DecoderTestError", " SampleTime: " +
                        decoderResult.bufferInfo.presentationTimeUs +
                        " RequestTime: " + SampleId +
                        " BytesSamplesChannels.length = " +
                        decoderResult.bytes.length);

                TestsResults[SampleId] = new TestResult(IsError,
                        decoderResult.bufferInfo.presentationTimeUs, Message);

                if (AllNotNull(TestsResults)) signal.countDown();
                TimeOutPass = true;
            }));
        }

        signal.await();

        int Max = 50;
        int count = TestsResults.length - Max;
        StringBuilder Results = new StringBuilder();
        for (int i = 1; i < Max; i++) {
            count++;
            if (TestsResults[count] != null)
                Results.append(TestsResults[count].Message);
            else {
                Results.append(" {NULL} ");
            }
            Results.append(",");
        }

        Log.i("TestsResults", Results.toString());
        decoderCodecWithCacheManager.clear();

        for (int i = 0; i < TestsResults.length; i++) {
            TestResult testResult = TestsResults[i];
            if (testResult == null || testResult.IsError) {
                String message = "Is NUll " + i;
                if (testResult != null)
                    message = testResult.Message;
                Log.e("Result", message);
                fail();
            }
        }
    }
}
