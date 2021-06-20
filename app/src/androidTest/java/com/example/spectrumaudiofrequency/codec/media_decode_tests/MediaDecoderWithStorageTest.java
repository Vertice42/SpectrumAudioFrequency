package com.example.spectrumaudiofrequency.codec.media_decode_tests;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.codec.CodecTestResult;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoderWithStorage;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoderWithStorage.PeriodRequest;
import com.example.spectrumaudiofrequency.util.PerformanceCalculator;
import com.example.spectrumaudiofrequency.util.VerifyTimeOut;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;

import static com.example.spectrumaudiofrequency.codec.CodecManagersTests.SoundID;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class MediaDecoderWithStorageTest {
    private static final long MAX_TIME = 5000;
    private final MediaDecoderWithStorage decoder;

    public MediaDecoderWithStorageTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        decoder = new MediaDecoderWithStorage(context, SoundID);
        decoder.start();
    }

    public void makeRequests(int offset,
                             int length,
                             long MediaDuration,
                             PerformanceCalculator performanceCalculator,
                             VerifyTimeOut verifyTimeOut,
                             LinkedList<CodecTestResult> DecoderResults) {

        for (int i = offset; i < length; i++) {
            decoder.makeRequest(new PeriodRequest(i, decoderResult -> {
                verifyTimeOut.Pass();
                if (decoderResult.bufferInfo != null) {
                    long presentationTimeUs = decoderResult.bufferInfo.presentationTimeUs;
                    performanceCalculator.stop(presentationTimeUs, MediaDuration).logPerformance();
                    performanceCalculator.start();
                    DecoderResults.add(new CodecTestResult(presentationTimeUs,
                            decoderResult.bytes.length,
                            decoderResult.bufferInfo.size));
                } else DecoderResults.add(null);
            }));
        }
    }

    @Test
    public void addRequestsTest() throws InterruptedException {
        CountDownLatch wantingResults = new CountDownLatch(1);
        LinkedList<CodecTestResult> RequestsResults = new LinkedList<>();
        int numberOfSamples = decoder.getNumberOfSamples();
        Assert.assertTrue(numberOfSamples > 0);

        VerifyTimeOut verifyTimeOut = new VerifyTimeOut(this.getClass(),
                MAX_TIME,
                wantingResults,
                false);

        if (decoder.IsDecoded()) {
            decoder.clear();
            fail();
        }
        PerformanceCalculator performanceCalculator;
        performanceCalculator = new PerformanceCalculator("DecoderWithStorage");

        makeRequests(0,
                numberOfSamples - 1,
                (long) decoder.getTrueMediaDuration(),
                performanceCalculator,
                verifyTimeOut,
                RequestsResults);

        decoder.addOnDecoderFinishListener(() -> {
            int TrueNumberOfSamples = decoder.getNumberOfSamples();
            int lastSample = TrueNumberOfSamples - 1;
            //if it is necessary to make more requests
            if (TrueNumberOfSamples > numberOfSamples) {
                int additionalRequests = TrueNumberOfSamples - numberOfSamples;
                makeRequests(numberOfSamples - additionalRequests,
                        lastSample,
                        decoder.MediaDuration,
                        performanceCalculator,
                        verifyTimeOut,
                        RequestsResults);
            } else if (TrueNumberOfSamples < numberOfSamples) lastSample++;

            decoder.makeRequest(new PeriodRequest(lastSample, lastDecoderResult -> {
                if (lastDecoderResult.bufferInfo != null) {
                    RequestsResults.add(new CodecTestResult(
                            lastDecoderResult.bufferInfo.presentationTimeUs,
                            lastDecoderResult.bytes.length,
                            lastDecoderResult.bufferInfo.size));
                } else {
                    RequestsResults.add(null);
                }
                wantingResults.countDown();
            }));
        });

        wantingResults.await();
        int TrueNumberOfSamples = decoder.getNumberOfSamples();
        decoder.clear();
        decoder.close();
        CodecErrorChecker.check(getClass().getSimpleName(), RequestsResults);
    }
}
