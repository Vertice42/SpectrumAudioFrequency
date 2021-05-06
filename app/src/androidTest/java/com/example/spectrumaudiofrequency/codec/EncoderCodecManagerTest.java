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
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

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
//        newFormat.setInteger(MediaFormat.KEY_BIT_RATE, newFormat.getInteger(MediaFormat.KEY_BIT_RATE) * 2);

        Log.i("oldFormat", oldFormat.toString());
        Log.i("newFormat", newFormat.toString());

        this.Encoder = new EncoderCodecManager(newFormat);
    }

    private boolean AlreadyCoded(ArrayList<TestResult> testResults, long SampleTime) {
        for (int i = 0; i < testResults.size(); i++) {
            TestResult testResult = testResults.get(i);
            if (testResult.SampleTime == SampleTime) return true;
            else if (testResult.SampleTime > SampleTime) return false;
        }
        return false;
    }

    @Test
    public void Encode() throws InterruptedException {
        final CountDownLatch signal = new CountDownLatch(1);

        ArrayList<TestResult> testResults = new ArrayList<>();

        byte[] inputData = new byte[Encoder.getInputBufferLimit()];
        for (int i = 0; i < inputData.length; i++) inputData[i] = (byte) (i + i / 2);

        int Samples = 100;

        Encoder.addOutputListener(codecSample -> {

            Log.i("OutputListener", "" + codecSample.bufferInfo.presentationTimeUs);

            boolean IsError = false;
            String message = "";
            if (codecSample.bytes.length < 1) {
                IsError = true;
                message += " Sample Samples == 0";
            }
            long timeUs = codecSample.bufferInfo.presentationTimeUs;

            if (AlreadyCoded(testResults, timeUs)) {
                IsError = true;
                message += " Sample Already Encoded " + timeUs;
            }
            testResults.add(new TestResult(IsError, timeUs, message));
        });

        for (int i = 0; i < Samples - 1; i++) {
            int finalI = i;
            Encoder.getInputBufferID(Id -> {
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                Log.i("put", "time: " + finalI * 1000);
                bufferInfo.set(0, inputData.length, finalI * 1000, MediaCodec.BUFFER_FLAG_KEY_FRAME);
                Encoder.putData(Id, bufferInfo, inputData);
            });
        }
        Encoder.getInputBufferID(Id -> {
            byte[] lastInputData = new byte[inputData.length / 2];
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.set(0, lastInputData.length, Samples * 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            Encoder.putData(Id, bufferInfo, lastInputData);
        });
        Encoder.stop();

        Encoder.addOnFinishListener(signal::countDown);

        signal.await();

        for (int i = 0; i < testResults.size(); i++)
            Log.i("testResult", testResults.size() + " results " + testResults.get(i).toString());


        for (int i = 0; i < testResults.size(); i++) {
            TestResult testResult = testResults.get(i);
            if (testResult.IsError) {
                Log.wtf("DecoderTestError", testResult.Message);
                Assert.assertFalse(testResult.IsError);
            }
        }

        Assert.assertEquals(testResults.size(), Samples);
    }
}
