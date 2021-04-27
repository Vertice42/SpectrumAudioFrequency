package com.example.spectrumaudiofrequency.codec;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.R;
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
    public static final int AudioId = R.raw.hollow;

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
        MediaFormatConverter mediaFormatConverter = new MediaFormatConverter(context, AudioId, newAudioFormat);

        final CountDownLatch signal = new CountDownLatch(1);
        mediaFormatConverter.setOnConvert(ConverterResult -> {
            if (!MediaMuxerManager.IsPrepared()) {
                MediaFormat outputFormat = mediaFormatConverter.getOutputFormat();
                Log.i("outputFormat", outputFormat.toString());

                MediaMuxerManager.prepare(cutoffs, outputFormat);
                MediaMuxerManager.putExtractorData();
            }
            MediaMuxerManager.writeSampleData(ConverterResult.bufferInfo, ConverterResult.OutputBuffer);

        });
        mediaFormatConverter.setFinishListener(() -> {
            signal.countDown();
            MediaMuxerManager.stop();
        });
        mediaFormatConverter.start();
        signal.await();
    }
}