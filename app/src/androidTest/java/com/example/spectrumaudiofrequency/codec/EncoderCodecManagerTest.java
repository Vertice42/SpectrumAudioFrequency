package com.example.spectrumaudiofrequency.codec;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.R;
import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager;
import com.example.spectrumaudiofrequency.core.codec_manager.EncoderCodecManager;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

@RunWith(AndroidJUnit4.class)
public class EncoderCodecManagerTest {
    private final EncoderCodecManager Encoder;
    private static final int TEST_RAW_ID = R.raw.stardew_valley;

    public EncoderCodecManagerTest() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(context, getUriFromResourceId(context, TEST_RAW_ID), null);

        MediaFormat oldFormat = mediaExtractor.getTrackFormat(0);

        MediaFormat newFormat = CodecManager.copyMediaFormat(oldFormat);
        newFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);

        Log.i("oldFormat", oldFormat.toString());
        Log.i("newFormat", newFormat.toString());

        this.Encoder = new EncoderCodecManager(newFormat);
    }

    @Test
    public void Encode() throws InterruptedException {
        final CountDownLatch signal = new CountDownLatch(1);

        byte[] inputData = new byte[4096/2];
        for (int i = 0; i < inputData.length; i++) {
            inputData[i] = (byte) (i + i / 2);
        }

        int length = 100;
        AtomicInteger count = new AtomicInteger(0);

        AtomicBoolean OK = new AtomicBoolean(true);
        for (int i = 0; i < length; i++) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.set(0, inputData.length, 1000 * i, MediaCodec.BUFFER_FLAG_KEY_FRAME);

            Encoder.getInputBuffer((bufferId, inputBuffer) -> {
                inputBuffer.clear();
                inputBuffer.put(inputData);
                bufferInfo.size = inputData.length;
                Encoder.processInput(new CodecManager.CodecManagerRequest(bufferId,bufferInfo));
            });

            Encoder.addOnOutputListener(encoderResult -> {
                Log.v("encoderResult", encoderResult.toString());

                byte[] EncoderResult = new byte[encoderResult.OutputBuffer.remaining()];
                encoderResult.OutputBuffer.get(EncoderResult);

                if (EncoderResult.length < 1) OK.set(false);

                count.getAndIncrement();
                if (count.get() >= length) signal.countDown();
            });
        }
        signal.await();

        Assert.assertTrue(OK.get());
    }
}
