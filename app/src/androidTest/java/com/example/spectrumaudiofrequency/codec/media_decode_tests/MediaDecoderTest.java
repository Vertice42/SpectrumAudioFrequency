package com.example.spectrumaudiofrequency.codec.media_decode_tests;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.codec.CodecTestResult;
import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager;
import com.example.spectrumaudiofrequency.util.PerformanceCalculator;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

import static com.example.spectrumaudiofrequency.codec.CodecManagersTests.SoundID;
import static com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.DecoderResult.SampleChannelsToBytes;
import static com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.DecoderResult.separateSampleChannels;

@RunWith(AndroidJUnit4.class)
public class MediaDecoderTest {
    private static final long MAX_TIME_OUT = 50000;
    private final ForkJoinPool forkJoinPool;
    private final DecoderManager decoder;
    private final DecoderManager decodeWithRearrangement;
    private final boolean TimeOutEnable = false;
    private boolean TimeOutPass = false;

    public MediaDecoderTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        decoder = new DecoderManager(context, SoundID);

        decodeWithRearrangement = new DecoderManager(context, SoundID);

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

    private void TestDecoding(DecoderManager decoder) throws InterruptedException {
        LinkedList<CodecTestResult> DecoderResults = new LinkedList<>();
        CountDownLatch countDownLatch = new CountDownLatch(1);

        PerformanceCalculator performanceCalculator = new PerformanceCalculator("Decoder");

        decoder.addOnDecodingListener(decoderResult -> {
            long presentationTimeUs = decoderResult.bufferInfo.presentationTimeUs;

            performanceCalculator.stop(presentationTimeUs, ((long) decoder.getTrueMediaDuration()))
                    .logPerformance();
            performanceCalculator.start();

            DecoderResults.add(new CodecTestResult(presentationTimeUs,
                    decoderResult.bytes.length, decoderResult.bufferInfo.flags
            ));
        });

        decoder.addOnDecoderFinishListener(countDownLatch::countDown);

        CountTimeout(countDownLatch);
        decoder.start();
        countDownLatch.await();
        Assert.assertEquals(decoder.getNumberOfSamples(), DecoderResults.size());
        CodecErrorChecker.check(this.getClass().getSimpleName(), DecoderResults);
    }

    @Test
    public void decode() throws InterruptedException {
        TestDecoding(decoder);
    }

    @Test
    public void decodeWithRearrangement() throws InterruptedException {
        decodeWithRearrangement.setSampleRearranger(metrics ->
                new CodecManager.SampleMetrics((metrics.SampleDuration / 2), (int) Math.ceil(((double)
                        metrics.SampleSize * metrics.SampleDuration)
                        / metrics.SampleDuration / 2f)));
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