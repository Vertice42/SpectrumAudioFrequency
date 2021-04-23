package com.example.spectrumaudiofrequency;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.core.MediaMuxerManager;
import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaFormatConverter;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

@RunWith(AndroidJUnit4.class)
public class MediaMuxerTest {
    public static final int AudioId = R.raw.choose;

    @Test
    public void MainTest() throws IOException, InterruptedException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MediaMuxerManager MediaMuxerManager = new MediaMuxerManager(context,
                getUriFromResourceId(context, R.raw.video_input3));

        MediaMuxerManager.Cutoff[] cutoffs = new MediaMuxerManager.Cutoff[5];

        long cutTime = MediaMuxerManager.getVideoDuration() / cutoffs.length;
        long time = cutTime;

        for (int i = 0; i < cutoffs.length; i++) {
            cutoffs[i] = new MediaMuxerManager.Cutoff(time, time + 23220 * 10);
            time += cutTime;
        }

        MediaExtractor extraAudioExtractor = new MediaExtractor();
        extraAudioExtractor.setDataSource(context, getUriFromResourceId(context, AudioId), null);
        MediaFormat originalFormat = extraAudioExtractor.getTrackFormat(0);

        Log.i("originalFormat", originalFormat + "");

        MediaFormat newAudioFormat = CodecManager.copyMediaFormat(originalFormat);
        newAudioFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        //newAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, originalFormat.getInteger(MediaFormat.KEY_BIT_RATE) * 2);

        MediaFormatConverter mediaFormatConverter = new MediaFormatConverter(context, AudioId, newAudioFormat);

        final CountDownLatch signal = new CountDownLatch(1);
        mediaFormatConverter.setOnConvert((end, ConverterResult) -> {
            if (!MediaMuxerManager.IsPrepared()) {
                MediaFormat outputFormat = null;
                try {
                    outputFormat = mediaFormatConverter.getOutputFormat();
                    Log.i("outputFormat", outputFormat.toString());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                MediaMuxerManager.prepare(cutoffs, outputFormat);
                MediaMuxerManager.putExtractorData();
            }

            if (end) {
                signal.countDown();
                Log.i("on end", "" + ConverterResult.toString());
                MediaMuxerManager.stop();
            } else {
                MediaMuxerManager.writeSampleData(ConverterResult.bufferInfo, ConverterResult.OutputBuffer);
            }
        });
        mediaFormatConverter.start();

        signal.await();
    }
}