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
import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.SampleMetrics;
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
    private final SampleMetrics sampleMetrics;
    private final int SampleToEncode = 100;

    public MediaEncoderTest() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(context, getUriFromResourceId(context, TEST_RAW_ID), null);

        MediaFormat oldFormat = mediaExtractor.getTrackFormat(0);

        MediaFormat newFormat = CodecManager.copyMediaFormat(oldFormat);
        newFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        int mediaDurationToTest = 25000 * SampleToEncode;
        newFormat.setLong(MediaFormat.KEY_DURATION, mediaDurationToTest);

        Log.i("oldFormat", oldFormat.toString());
        Log.i("newFormat", newFormat.toString());

        Encoder = new EncoderCodecManager(newFormat);
        sampleMetrics = new SampleMetrics(25000, Encoder.getInputBufferLimit());
        Encoder.setSampleMetrics(sampleMetrics);
    }

    @Test
    public void Encode() throws InterruptedException {
        final CountDownLatch EndSignal = new CountDownLatch(1);

        LinkedList<CodecTestResult> EncoderTestResults = new LinkedList<>();
        PerformanceCalculator performanceCalculator = new PerformanceCalculator("Encode");

        byte[] inputData = new byte[sampleMetrics.SampleSize];
        for (int i = 0; i < inputData.length; i++) inputData[i] = (byte) (i + i / 2);

        Encoder.addOnEncode(codecSample -> {
            long presentationTimeUs = codecSample.bufferInfo.presentationTimeUs;
            performanceCalculator.stop(presentationTimeUs, Encoder.MediaDuration).logPerformance();
            performanceCalculator.start();
            EncoderTestResults.add(new CodecTestResult(presentationTimeUs,
                    codecSample.bytes.length,
                    codecSample.bufferInfo.flags));
        });
        Encoder.addOnFinishListener(EndSignal::countDown);

        for (int i = 0; i < SampleToEncode; i++) Encoder.addPutInputRequest(inputData);
        Encoder.stop();

        EndSignal.await();

        Assert.assertEquals(EncoderTestResults.size(), SampleToEncode);
        CodecErrorChecker.check(this.getClass().getSimpleName(), EncoderTestResults);
    }
}
