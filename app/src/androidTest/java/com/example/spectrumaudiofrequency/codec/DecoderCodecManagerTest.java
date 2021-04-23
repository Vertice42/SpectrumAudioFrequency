package com.example.spectrumaudiofrequency.codec;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.R;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderCodecManager;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderCodecManager.PeriodRequest;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class DecoderCodecManagerTest {
    private final ForkJoinPool forkJoinPool;
    private final DecoderCodecManager decoderCodecManager;

    public DecoderCodecManagerTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        int id = R.raw.choose;
        decoderCodecManager = new DecoderCodecManager(context, id);

        forkJoinPool = ForkJoinPool.commonPool();
    }

    private boolean AllNotNull(Object[] objects) {
        for (Object object : objects) if (object == null) return false;
        return true;
    }

    long maxTime = 1000;
    private boolean Pass = false;

    void Wait(CountDownLatch countDownLatch) {
        forkJoinPool.execute(() -> {
            try {
                Thread.sleep(maxTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (Pass) Wait(countDownLatch);
            else countDownLatch.countDown();
            Pass = false;
        });
    }

    static class TestResult {
        boolean IsError;
        String Message;

        public TestResult(boolean isError, String message) {
            IsError = isError;
            Message = message;
        }

        @Override
        public @NotNull String toString() {
            return (IsError) ? "ERROR " : "" + Message;
        }
    }

    @Test
    public void addRequest() throws InterruptedException {
        final CountDownLatch signal = new CountDownLatch(1);

        int RequestsNumber = decoderCodecManager.getSampleLength();

        TestResult[] TestsResults = new TestResult[RequestsNumber];

        Wait(signal);

        for (int i = 0; i < RequestsNumber; i++) {
            int SamplePeace = i;

            decoderCodecManager.addRequest(new PeriodRequest(SamplePeace, decoderResult -> {
                boolean IsError = false;
                String Message = "{" + SamplePeace;
                if (!decoderResult.SampleTimeNotExist()) {
                    if (decoderResult.Sample.length <= 0) {
                        Message += "size 0";
                        IsError = true;
                    }
                    short[][] data = decoderResult.getSampleChannels(decoderCodecManager);
                } else {
                    Message += " SampleTimeNotExist";
                }
                Message += " }";

                if (IsError) Log.e("DecoderTestError", " SampleTime: " +
                        decoderResult.bufferInfo.presentationTimeUs +
                        " RequestTime: " + SamplePeace +
                        " BytesSamplesChannels.length = " +
                        decoderResult.Sample.length);

                TestsResults[SamplePeace] = new TestResult(IsError, Message);

                if (AllNotNull(TestsResults)) signal.countDown();
                Pass = true;
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
        decoderCodecManager.clear();

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
