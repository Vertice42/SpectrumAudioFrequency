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

import com.example.spectrumaudiofrequency.core.codec_manager.MediaFormatConverter.MediaFormatConverterFinishListener;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;

@RequiresApi(api = Build.VERSION_CODES.M)
public class MediaMuxerManager {
    private final MediaExtractor[] mediaExtractors;

    private MediaMuxer mediaMuxer;
    private MediaFormat VideoFormat;
    private Cutoff[] cutoffs;

    private long outPutDuration;
    private ByteBuffer inputBuffer;
    private int ExternalMediaTrackId;
    private boolean IsPrepared = false;
    private int VideoTrackId;

    public MediaMuxerManager(Context context, Uri VideoUri) {

        mediaExtractors = new MediaExtractor[1];

        mediaExtractors[0] = new MediaExtractor();
        try {
            mediaExtractors[0].setDataSource(context, VideoUri, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

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

        inputBuffer = ByteBuffer.allocate(1024 * 5000);

        this.cutoffs = cutoffs;

        String outputPath = createFile("test.mp4");
        try {
            mediaMuxer = new MediaMuxer(outputPath, OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }

        long skippedTime = 0;
        for (Cutoff value : cutoffs) skippedTime += value.getTimeSkip();

        VideoFormat = mediaExtractors[0].getTrackFormat(0);

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
        return mediaExtractors[0].getTrackFormat(0).getLong(MediaFormat.KEY_DURATION);
    }

    private void skipTime(long timeBefore, Cutoff cutoff, MediaExtractor mediaExtractor) {
        while (true) {
            timeBefore = mediaExtractor.getSampleTime();
            //todo o audio fica desincronisado por causa das diferensas de tempo da flag
            if (timeBefore <= cutoff.endTime || mediaExtractor.getSampleFlags() != MediaExtractor.SAMPLE_FLAG_SYNC) {
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
        for (MediaExtractor mediaExtractor : mediaExtractors) mediaExtractor.selectTrack(0);

        long presentationTimeUs = mediaExtractors[0].getSampleTime();
        long timeBefore = 0;
        long sampleDuration = 0;
        long time_after = 0;

        while (true) {

            float progress = ((float) presentationTimeUs / outPutDuration) * 100;
            int bufferSize = mediaExtractors[0].readSampleData(inputBuffer, 0);


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
                mediaExtractors[0].release();
                break;
            }

            Cutoff cutoff = SearchForCutOff(timeBefore, sampleDuration);

            if (cutoff != null) {
                skipTime(timeBefore, cutoff, mediaExtractors[0]);
            } else {
                mediaExtractors[0].advance();
            }
            time_after = mediaExtractors[0].getSampleTime();
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
