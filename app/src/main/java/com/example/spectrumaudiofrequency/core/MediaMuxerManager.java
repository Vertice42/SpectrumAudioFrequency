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

import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaFormatConverter;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaFormatConverter.MediaFormatConverterFinishListener;
import com.example.spectrumaudiofrequency.util.PerformanceCalculator;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;
import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

@RequiresApi(api = Build.VERSION_CODES.M)
public class MediaMuxerManager {
    private final MediaExtractor videoExtractor;
    private final MediaFormatConverter formatConverter;

    private MediaMuxer mediaMuxer;
    private MediaFormat VideoFormat;
    private Cutoff[] cutoffs;
    private ByteBuffer inputBuffer;

    private MediaFormatConverterFinishListener finishListener;
    private long outPutDuration;
    private int ExternalMediaTrackId;
    private int VideoTrackId;
    private boolean IsPrepared = false;

    public MediaMuxerManager(Context context, Uri VideoUri, int[] IdsOfSounds) {
        videoExtractor = new MediaExtractor();
        MediaFormat[] mediaFormats = new MediaFormat[IdsOfSounds.length];

        try {
            videoExtractor.setDataSource(context, VideoUri, null);
            for (int i = 0; i < IdsOfSounds.length; i++) {
                MediaExtractor extraExtractor = new MediaExtractor();
                extraExtractor.setDataSource(context, getUriFromResourceId(context, IdsOfSounds[i]), null);
                mediaFormats[i] = extraExtractor.getTrackFormat(0);
                Log.i("Sound Format " + i, mediaFormats[i].toString());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat newAudioFormat = CodecManager.copyMediaFormat(mediaFormats[0]);

        newAudioFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);

        formatConverter = new MediaFormatConverter(context, IdsOfSounds, newAudioFormat);
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
            outPutDuration = VideoFormat.getLong(MediaFormat.KEY_DURATION) - skippedTime;
            VideoFormat.setLong(MediaFormat.KEY_DURATION, outPutDuration);
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
        return videoExtractor.getTrackFormat(0).getLong(MediaFormat.KEY_DURATION);
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

    public void putExtractorData(MediaFormatConverterFinishListener formatConverterFinishListener) {
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
        formatConverterFinishListener.OnFinish();
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

    public void setFinishListener(MediaFormatConverterFinishListener finishListener) {
        this.finishListener = finishListener;
    }

    public void start(Cutoff[] cutoffs) {

        ArrayList<CodecManager.CodecSample> cacheOfSamples = new ArrayList<>();

        PerformanceCalculator performance = new PerformanceCalculator("MuxTime");

        MediaFormatConverter.MediaFormatConverterListener converterListener = converterResult -> {
            long presentationTimeUs = converterResult.bufferInfo.presentationTimeUs;
            performance.stop(presentationTimeUs,
                    formatConverter.getMediaDuration()).logPerformance(" flag: " +
                    converterResult.bufferInfo.flags +
                    " size:" + converterResult.bufferInfo.size);
            performance.start();
            this.writeSampleData(converterResult.bufferInfo, converterResult.bytes);
        };

        MediaFormatConverter.MediaFormatConverterListener waitingPreparationOfMediaMuxer = converterResult -> {
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
        formatConverter.setOnConvert(waitingPreparationOfMediaMuxer);
        formatConverter.setFinishListener(() -> {
            this.finishListener.OnFinish();
            this.stop();
        });

        formatConverter.start();
        MediaFormat outputFormat = formatConverter.getOutputFormat();
        this.prepare(cutoffs, outputFormat);
        formatConverter.pause();
        this.putExtractorData(formatConverter::restart);
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
