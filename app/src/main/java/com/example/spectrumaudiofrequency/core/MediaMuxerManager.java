package com.example.spectrumaudiofrequency.core;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaMuxer.OutputFormat;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.example.spectrumaudiofrequency.core.codec_manager.AudioFormatConverter;
import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoderWithStorage;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaFormatConverter.MediaFormatConverterListener;
import com.example.spectrumaudiofrequency.util.PerformanceCalculator;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.TreeMap;

import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;
import static android.media.MediaFormat.KEY_DURATION;
import static android.media.MediaFormat.KEY_SAMPLE_RATE;
import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

@RequiresApi(api = Build.VERSION_CODES.M)
public class MediaMuxerManager {
    private final MediaExtractor videoExtractor;
    private final AudioFormatConverter formatConverter;

    private MediaMuxer mediaMuxer;
    private MediaFormat VideoFormat;
    private Cutoff[] cutoffs;
    private ByteBuffer inputBuffer;

    private Runnable finishListener;
    private long outPutDuration;
    private int ExternalMediaTrackId;
    private int VideoTrackId;
    private boolean IsPrepared = false;

    public MediaMuxerManager(Context context,
                             String VideoName,
                             Uri VideoUri,
                             int[] IdsOfExtraSounds) {
        videoExtractor = new MediaExtractor();
        int AudioIndex = 1;//todo index pode ser diferente
        MediaFormat FormatWithLongerSampleRate = new MediaFormat();
        long BiggestMediaDuration = 0;

        try {
            videoExtractor.setDataSource(context, VideoUri, null);

            {
                FormatWithLongerSampleRate = videoExtractor.getTrackFormat(AudioIndex);
                int BiggestSampleRate = FormatWithLongerSampleRate.getInteger(KEY_SAMPLE_RATE);
                BiggestMediaDuration = FormatWithLongerSampleRate.getLong(KEY_DURATION);

                MediaExtractor extraExtractor = new MediaExtractor();
                for (int idsOfExtraSound : IdsOfExtraSounds) {
                    extraExtractor.setDataSource(context,
                            getUriFromResourceId(context, idsOfExtraSound),
                            null);

                    MediaFormat format = extraExtractor.getTrackFormat(0);

                    int sampleRate = format.getInteger(KEY_SAMPLE_RATE);

                    if (sampleRate > BiggestSampleRate) {
                        BiggestSampleRate = sampleRate;
                        FormatWithLongerSampleRate = format;
                    }

                    long mediaDuration = format.getLong(KEY_DURATION);

                    if (mediaDuration > BiggestMediaDuration) {
                        BiggestMediaDuration = mediaDuration;
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        MediaFormat newAudioFormat = CodecManager.copyMediaFormat(FormatWithLongerSampleRate);

        newAudioFormat.setLong(KEY_DURATION, BiggestMediaDuration);
        newAudioFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);

        ArrayList<String> mediaNames = new ArrayList<>();
        TreeMap<String, MediaDecoderWithStorage> decoders = new TreeMap<>();

        MediaDecoderWithStorage DecoderOfVideoSound = new MediaDecoderWithStorage(context,
                VideoUri,
                (VideoName + AudioIndex), AudioIndex);
        mediaNames.add(DecoderOfVideoSound.MediaName);
        decoders.put(DecoderOfVideoSound.MediaName, DecoderOfVideoSound);

        for (int idsOfExtraSound : IdsOfExtraSounds) {
            MediaDecoderWithStorage decoder = new MediaDecoderWithStorage(context,
                    idsOfExtraSound,
                    0);
            mediaNames.add(decoder.MediaName);
            decoders.put(decoder.MediaName, decoder);
        }

        formatConverter = new AudioFormatConverter(mediaNames, decoders, newAudioFormat);
    }

    private String createFile(String FileName) {
        String OUTPUT_FILENAME_DIR = "/storage/emulated/0/Download/";
        File dir = new File(OUTPUT_FILENAME_DIR);
        if (!dir.exists()) {
            if (!dir.mkdirs())
                Log.e("createFileError", "error creating file or directory");
        } else if (dir.delete()) return createFile(FileName);
        File file = new File(dir, FileName);
        return file.getPath();
    }

    public void prepare(Cutoff[] cutoffs, MediaFormat ExternalMediaFormat) {
        this.cutoffs = cutoffs;

        inputBuffer = ByteBuffer.allocate(1024 * 50_000);

        String outputPath = createFile("test.mp4");
        try {
            mediaMuxer = new MediaMuxer(outputPath, OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }

        long skippedTime = 0;
        for (Cutoff value : cutoffs) skippedTime += value.getTimeSkip();

        VideoFormat = videoExtractor.getTrackFormat(0);

        String mime = VideoFormat.getString(MediaFormat.KEY_MIME);

        if (mime.contains("video")) {
            outPutDuration = VideoFormat.getLong(KEY_DURATION) - skippedTime;
            VideoFormat.setLong(KEY_DURATION, outPutDuration);
        }

        VideoTrackId = mediaMuxer.addTrack(VideoFormat);

        if (ExternalMediaFormat != null) {
            ExternalMediaTrackId = mediaMuxer.addTrack(ExternalMediaFormat);
        }

        mediaMuxer.start();
        IsPrepared = true;
    }

    public boolean IsPrepared() {
        return IsPrepared;
    }

    public long getVideoDuration() {
        return videoExtractor.getTrackFormat(0).getLong(KEY_DURATION);
    }

    private void skipTime(Cutoff cutoff, MediaExtractor mediaExtractor) {
        while (true) {
            long timeBefore = mediaExtractor.getSampleTime();
            //todo o audio fica desincronisado por causa das diferensas de tempo da flag
            if (timeBefore <= cutoff.endTime ||
                    mediaExtractor.getSampleFlags() != MediaExtractor.SAMPLE_FLAG_SYNC) {
                mediaExtractor.advance();
            } else break;
        }
    }

    private Cutoff SearchForCutOff(long timeBefore, long SampleDuration) {
        for (Cutoff cutoff : cutoffs) {
            if (cutoff.starTime >= timeBefore && cutoff.starTime <= timeBefore + SampleDuration)
                return cutoff;
        }
        return null;
    }

    public void putVideoData(Runnable formatConverterFinishListener) {
        videoExtractor.selectTrack(0);

        long presentationTimeUs = videoExtractor.getSampleTime();
        long timeBefore = 0;
        long sampleDuration = 0;
        long time_after = 0;

        while (true) {

            float progress = ((float) presentationTimeUs / outPutDuration) * 100;
            int bufferSize = videoExtractor.readSampleData(inputBuffer, 0);


            if (bufferSize > 0 && progress < 100) {

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                bufferInfo.set(0, bufferSize, presentationTimeUs, BUFFER_FLAG_KEY_FRAME);

                Log.i("MuxerVideoProgress",
                        +progress + "%"
                                + " presentationTimeUs: " + bufferInfo.presentationTimeUs
                                + " mime:" + VideoFormat.getString(MediaFormat.KEY_MIME)
                                + " flags " + bufferInfo.flags
                                + " bufferSize: " + bufferInfo.size
                );

                writeSampleData(VideoTrackId, inputBuffer, bufferInfo);

                sampleDuration = time_after - timeBefore;
                presentationTimeUs += sampleDuration;
                timeBefore = time_after;

            } else if (progress != 0) {
                videoExtractor.release();
                break;
            }

            Cutoff cutoff = SearchForCutOff(timeBefore, sampleDuration);

            if (cutoff != null) {
                skipTime(cutoff, videoExtractor);
            } else {
                videoExtractor.advance();
            }
            time_after = videoExtractor.getSampleTime();
        }
        formatConverterFinishListener.run();
    }

    public synchronized void writeSampleData(int TrackId, ByteBuffer inputBuffer,
                                             MediaCodec.BufferInfo bufferInfo) {
        mediaMuxer.writeSampleData(TrackId, inputBuffer, bufferInfo);
    }

    public synchronized void writeSampleData(MediaCodec.BufferInfo bufferInfo, byte[] data) {
        inputBuffer.clear();
        inputBuffer.put(data);
        writeSampleData(ExternalMediaTrackId, inputBuffer, bufferInfo);
    }

    public void setFinishListener(Runnable finishListener) {
        this.finishListener = finishListener;
    }

    public void start(Cutoff[] cutoffs) {

        ArrayList<CodecManager.CodecSample> cacheOfSamples = new ArrayList<>();

        PerformanceCalculator performance = new PerformanceCalculator("MuxTime");

        MediaFormatConverterListener converterListener = converterResult -> {
            long presentationTimeUs = converterResult.bufferInfo.presentationTimeUs;
            performance.stop(presentationTimeUs,
                    formatConverter.MediaDuration()).logPerformance(" flag: " +
                    converterResult.bufferInfo.flags +
                    " size:" + converterResult.bufferInfo.size);
            performance.start();
            this.writeSampleData(converterResult.bufferInfo, converterResult.bytes);
        };

        MediaFormatConverterListener waitingMediaMuxerPreparation = converterResult -> {
            if (this.IsPrepared()) {
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
        formatConverter.setOnConvert(waitingMediaMuxerPreparation);
        formatConverter.setFinishListener(() -> {
            this.finishListener.run();
            this.stop();
        });

        formatConverter.start();
        MediaFormat outputFormat = formatConverter.getOutputFormat();
        this.prepare(cutoffs, outputFormat);
        formatConverter.pause();
        this.putVideoData(formatConverter::restart);
    }

    public void stop() {
        mediaMuxer.stop();
        mediaMuxer.release();
    }

    public static class Cutoff {
        public long starTime;
        public long endTime;

        public Cutoff(long starTime, long endTime) {
            this.starTime = starTime;
            this.endTime = endTime;
        }

        public long getTimeSkip() {
            return endTime - starTime;
        }

        @Override
        public @NotNull String toString() {
            return "Cutoff{" +
                    "time=" + starTime +
                    '}';
        }
    }

}
