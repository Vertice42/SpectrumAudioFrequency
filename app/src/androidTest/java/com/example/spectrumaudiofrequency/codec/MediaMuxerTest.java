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
import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.CodecSample;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaFormatConverter;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaFormatConverter.MediaFormatConverterListener;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

@RunWith(AndroidJUnit4.class)
public class MediaMuxerTest {
    public static final int AudioId1 = R.raw.hollow;
    public static final int AudioId2 = R.raw.game_description;

    @Test
    public void Mux() throws IOException, InterruptedException {
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

        MediaExtractor extraExtractor = new MediaExtractor();
        extraExtractor.setDataSource(context, getUriFromResourceId(context, AudioId1), null);
        MediaFormat format0 = extraExtractor.getTrackFormat(0);

        MediaExtractor extraExtractor1 = new MediaExtractor();
        extraExtractor1.setDataSource(context, getUriFromResourceId(context, AudioId2), null);
        MediaFormat format1 = extraExtractor1.getTrackFormat(0);

        Log.i("Formats0", format0.toString());
        Log.i("Formats1", format1.toString());

        MediaFormat newAudioFormat = CodecManager.copyMediaFormat(format0);
        newAudioFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);

        MediaFormatConverter FormatConverter = new MediaFormatConverter(context,
                new int[]{AudioId1},
                newAudioFormat);

        ArrayList<CodecSample> cacheOfSamples = new ArrayList<>();

        MediaFormatConverterListener PosMuxerStart = converterResult -> {
            Log.i("ConverterProgress", (double)
                    converterResult.bufferInfo.presentationTimeUs
                    / FormatConverter.getMediaDuration() * 100 + "%");
            MediaMuxerManager.writeSampleData(converterResult.bufferInfo, converterResult.bytes);
        };

        MediaFormatConverterListener PreMuxerStart = converterResult -> {
            if (MediaMuxerManager.IsPrepared()) {
                while (cacheOfSamples.size() > 0) {
                    PosMuxerStart.onConvert(cacheOfSamples.get(0));
                    cacheOfSamples.remove(0);
                }
                PosMuxerStart.onConvert(converterResult);
                FormatConverter.setOnConvert(PosMuxerStart);
            } else {
                cacheOfSamples.add(converterResult);
            }
        };

        FormatConverter.setOnConvert(PreMuxerStart);

        final CountDownLatch signal = new CountDownLatch(1);
        FormatConverter.setFinishListener(() -> {
            signal.countDown();
            MediaMuxerManager.stop();
        });
        FormatConverter.start();
        MediaFormat outputFormat = FormatConverter.getOutputFormat();
        Log.i("outputFormat", outputFormat.toString());
        MediaMuxerManager.prepare(cutoffs, outputFormat);
        MediaMuxerManager.putExtractorData();

        signal.await();
    }
}