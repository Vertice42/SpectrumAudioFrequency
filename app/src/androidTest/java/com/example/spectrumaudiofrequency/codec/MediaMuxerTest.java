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
import com.example.spectrumaudiofrequency.util.PerformanceCalculator;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

@RunWith(AndroidJUnit4.class)
public class MediaMuxerTest {
    public static final int[] IdsOfSounds = {R.raw.hollow, R.raw.game_description};

    @Test
    public void Mux() throws IOException, InterruptedException {
        final CountDownLatch EndSignal = new CountDownLatch(1);

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MediaMuxerManager mediaMuxerManager = new MediaMuxerManager(context,
                getUriFromResourceId(context, R.raw.video_input3));
        MediaMuxerManager.Cutoff[] cutoffs = new MediaMuxerManager.Cutoff[5];

        long cutTime = mediaMuxerManager.getVideoDuration() / cutoffs.length;
        long time = cutTime;

        for (int i = 0; i < cutoffs.length; i++) {
            cutoffs[i] = new MediaMuxerManager.Cutoff(time, time + 23220 * 10);
            time += cutTime;
        }

        MediaFormat[] mediaFormats = new MediaFormat[IdsOfSounds.length];
        for (int i = 0; i < IdsOfSounds.length; i++) {
            MediaExtractor extraExtractor = new MediaExtractor();
            extraExtractor.setDataSource(context, getUriFromResourceId(context, IdsOfSounds[i]), null);
            mediaFormats[i] = extraExtractor.getTrackFormat(0);
            Log.i("Sound Format " + i, mediaFormats[i].toString());
        }

        MediaFormat newAudioFormat = CodecManager.copyMediaFormat(mediaFormats[0]);

        newAudioFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);

        MediaFormatConverter formatConverter = new MediaFormatConverter(context,
                new int[]{IdsOfSounds[0]}, newAudioFormat);

        ArrayList<CodecSample> cacheOfSamples = new ArrayList<>();

        PerformanceCalculator performance = new PerformanceCalculator("MuxTime");

        MediaFormatConverterListener converterListener = converterResult -> {
            long presentationTimeUs = converterResult.bufferInfo.presentationTimeUs;
            performance.stop(presentationTimeUs,
                    formatConverter.getMediaDuration()).logPerformance(" flag: " +
                    converterResult.bufferInfo.flags +
                    " size:" + converterResult.bufferInfo.size);
            performance.start();
            mediaMuxerManager.writeSampleData(converterResult.bufferInfo, converterResult.bytes);
        };

        MediaFormatConverterListener waitingPreparationOfMediaMuxer = converterResult -> {
            if (mediaMuxerManager.IsPrepared()) {
                while (cacheOfSamples.size() > 0) {
                    converterListener.onConvert(cacheOfSamples.get(0));
                    cacheOfSamples.remove(0);
                }
                converterListener.onConvert(converterResult);
                formatConverter.setOnConvert(converterListener);
            } else {
                cacheOfSamples.add(converterResult);
            }
        };
        formatConverter.setOnConvert(waitingPreparationOfMediaMuxer);

        formatConverter.setFinishListener(() -> {
            EndSignal.countDown();
            mediaMuxerManager.stop();
        });

        formatConverter.start();
        MediaFormat outputFormat = formatConverter.getOutputFormat();
        mediaMuxerManager.prepare(cutoffs, outputFormat);
        formatConverter.pause();
        mediaMuxerManager.putExtractorData(formatConverter::restart);

        EndSignal.await();
    }
}