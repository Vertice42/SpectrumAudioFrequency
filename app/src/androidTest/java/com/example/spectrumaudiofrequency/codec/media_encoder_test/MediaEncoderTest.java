package com.example.spectrumaudiofrequency.codec.media_encoder_test;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.R;
import com.example.spectrumaudiofrequency.codec.CodecTestResult;
import com.example.spectrumaudiofrequency.codec.media_decode_tests.CodecErrorChecker;
import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager;
import com.example.spectrumaudiofrequency.core.codec_manager.EncoderCodecManager;
import com.example.spectrumaudiofrequency.util.PerformanceCalculator;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;

import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

@RunWith(AndroidJUnit4.class)
public class MediaEncoderTest {
    private static final int TEST_RAW_ID = R.raw.stardew_valley;
    private final EncoderCodecManager Encoder;
    int SampleDuration = 2500;
    int MediaDurationToTest = SampleDuration * 100;

    public MediaEncoderTest() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(context, getUriFromResourceId(context, TEST_RAW_ID), null);

        MediaFormat oldFormat = mediaExtractor.getTrackFormat(0);

        MediaFormat newFormat = CodecManager.copyMediaFormat(oldFormat);
        newFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        newFormat.setLong(MediaFormat.KEY_DURATION, MediaDurationToTest);

        Log.i("oldFormat", oldFormat.toString());
        Log.i("newFormat", newFormat.toString());

        Encoder = new EncoderCodecManager(newFormat);
        Encoder.setSampleDuration(SampleDuration);
    }

    @Test
    public void Encode() throws InterruptedException {
        final CountDownLatch EndSignal = new CountDownLatch(1);

        LinkedList<CodecTestResult> DecoderTestResults = new LinkedList<>();
        PerformanceCalculator performanceCalculator = new PerformanceCalculator("Encode");

        byte[] inputData = new byte[Encoder.getInputBufferLimit()];
        for (int i = 0; i < inputData.length; i++) inputData[i] = (byte) (i + i / 2);
        int NumberOfSamples = MediaDurationToTest / SampleDuration;

        Encoder.addOnOutputListener(codecSample -> {
            long presentationTimeUs = codecSample.bufferInfo.presentationTimeUs;
            performanceCalculator.stop(presentationTimeUs, Encoder.MediaDuration).logPerformance();
            performanceCalculator.start();
            DecoderTestResults.add(new CodecTestResult(presentationTimeUs,
                    codecSample.bytes.length,
                    codecSample.bufferInfo.flags));
        });
        Encoder.addOnFinishListener(EndSignal::countDown);

        for (int i = 0; i < NumberOfSamples; i++) Encoder.addPutInputRequest(inputData);
        Encoder.stop();

        EndSignal.await();

        Assert.assertEquals(DecoderTestResults.size(), NumberOfSamples);
        CodecErrorChecker.check(this.getClass().getSimpleName(), DecoderTestResults);
    }
}
