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

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

@RequiresApi(api = Build.VERSION_CODES.M)
public class VideoMuxer {
    private final MediaExtractor[] mediaExtractors;
    private MediaMuxer mediaMuxer;
    private MediaFormat[] mediaFormats;
    private int NumberOfTracks = 0;
    private Cutoff[] cutoffs;
    private long outPutDuration;

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

    public VideoMuxer(Context context, Uri VideoUri, Uri AudioUri) {

        mediaExtractors = new MediaExtractor[1];

        mediaExtractors[0] = new MediaExtractor();
        try {
            mediaExtractors[0].setDataSource(context, VideoUri, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        /*
        mediaExtractors[1] = new MediaExtractor();
        try {
            mediaExtractors[1].setDataSource(context, AudioUri, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

         */

    }

    @Deprecated
    MediaFormat copyMediaFormat(MediaFormat mediaFormat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new MediaFormat(mediaFormat);
        } else {
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);

            MediaFormat r = null;

            if (mime.contains("video")) {
                int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
                int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                r = MediaFormat.createVideoFormat(mime, width, height);

                r.setInteger(MediaFormat.KEY_FRAME_RATE, mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE));
                if (r.containsKey(MediaFormat.KEY_CAPTURE_RATE))
                    r.setInteger(MediaFormat.KEY_CAPTURE_RATE, mediaFormat.getInteger(MediaFormat.KEY_CAPTURE_RATE));

            } else if (mime.contains("audio")) {
                int channelCount = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                int sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                r = MediaFormat.createAudioFormat(mime, sampleRate, channelCount);
            }

            r.setLong(MediaFormat.KEY_DURATION, mediaFormat.getLong(MediaFormat.KEY_DURATION));
            r.setByteBuffer("csd-0", mediaFormat.getByteBuffer("csd-0"));

            return r;
        }
    }

    private String createFile() {
        String TestFileName = "temp.mp4";
        String OUTPUT_FILENAME_DIR = "/storage/emulated/0/Download/";
        File dir = new File(OUTPUT_FILENAME_DIR);
        if (!dir.exists()) {
            if (!dir.mkdirs())
                Log.e("createFileError", "error creating file or directory");
        } else if (dir.delete()) return createFile();
        File file = new File(dir, TestFileName);
        return file.getPath();
    }

    public void prepare(Cutoff[] cutoffs) {
        this.cutoffs = cutoffs;

        String outputPath = createFile();
        try {
            mediaMuxer = new MediaMuxer(outputPath, OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }

        NumberOfTracks += mediaExtractors[0].getTrackCount();

        mediaFormats = new MediaFormat[NumberOfTracks];

        long skippedTime = 0;
        for (Cutoff value : cutoffs) skippedTime += value.getTimeSkip();

        for (int j = 0; j < mediaExtractors[0].getTrackCount(); j++) {
            MediaFormat trackFormat = mediaExtractors[0].getTrackFormat(j);

            mediaFormats[j] = trackFormat;

            if (mediaFormats[j].getString(MediaFormat.KEY_MIME).contains("video")) {
                outPutDuration = trackFormat.getLong(MediaFormat.KEY_DURATION) - skippedTime;
                mediaFormats[j].setLong(MediaFormat.KEY_DURATION, outPutDuration);
            } else {
                mediaFormats[j].setLong(MediaFormat.KEY_DURATION,
                        trackFormat.getLong(MediaFormat.KEY_DURATION) - skippedTime);
            }

            mediaMuxer.addTrack(mediaFormats[j]);
        }
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

    public void render() {
        ByteBuffer inputBuffer = ByteBuffer.allocate(100 * 1024);

        for (MediaExtractor mediaExtractor : mediaExtractors) mediaExtractor.selectTrack(0);

        int trackId = 0;
        int muxerTrackId = 0;
        int readExtractors = 0;
        long presentationTimeUs = mediaExtractors[readExtractors].getSampleTime();
        long timeBefore = 0;
        long sampleDuration = 0;
        long time_after = 0;

        mediaMuxer.start();
        while (true) {

            float progress = ((float) presentationTimeUs / outPutDuration) * 100;
            int bufferSize = mediaExtractors[readExtractors].readSampleData(inputBuffer, 0);

            if (bufferSize > 0 && progress < 100) {

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                bufferInfo.set(0, bufferSize, presentationTimeUs,
                        mediaExtractors[readExtractors].getSampleFlags());

                Log.i("Time",
                        +progress + "%"
                                + " presentationTimeUs: " + bufferInfo.presentationTimeUs
                                + " mime:" + mediaFormats[muxerTrackId].getString(MediaFormat.KEY_MIME)
                                + " flags " + bufferInfo.flags
                                + " outPutDuration: " + outPutDuration
                                + " bufferSize: " + bufferInfo.size
                                + "sampleDuration" + sampleDuration + "microns"
                );

                mediaMuxer.writeSampleData(muxerTrackId, inputBuffer, bufferInfo);

                sampleDuration = time_after - timeBefore;
                presentationTimeUs += sampleDuration;
                timeBefore = time_after;

            } else if (progress != 0) {
                trackId++;
                muxerTrackId++;

                if (trackId >= NumberOfTracks) {
                    mediaExtractors[readExtractors].release();
                    readExtractors++;

                    if (!(readExtractors < mediaExtractors.length)) break;

                    trackId = 0;
                    NumberOfTracks = mediaExtractors[readExtractors].getTrackCount();
                }
                mediaExtractors[readExtractors].selectTrack(trackId);
                timeBefore = 0;
                presentationTimeUs = 0;
            }

            Cutoff cutoff = SearchForCutOff(timeBefore, sampleDuration);

            if (cutoff != null) {
                skipTime(timeBefore, cutoff, mediaExtractors[readExtractors]);
            } else {
                mediaExtractors[readExtractors].advance();
            }

            time_after = mediaExtractors[readExtractors].getSampleTime();
        }

        mediaMuxer.stop();
        mediaMuxer.release();
    }
}
