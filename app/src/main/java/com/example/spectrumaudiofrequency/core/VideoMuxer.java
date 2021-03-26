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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC;

@RequiresApi(api = Build.VERSION_CODES.M)
public class VideoMuxer {
    private final String OUTPUT_FILENAME_DIR = "/storage/emulated/0/Download/";
    private final Context context;
    private final MediaExtractor[] mediaExtractors;

    public VideoMuxer(Context context, Uri VideoUri, Uri AudioUri) {
        this.context = context;

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

    public boolean encode() throws IOException {
        String TestFileName = "temp.mp4";
        File dir = new File(OUTPUT_FILENAME_DIR);
        if (!dir.exists()) if (!dir.mkdirs()) return false;
        File file = new File(dir, TestFileName);
        String outputPath = file.getPath();

        MediaMuxer mediaMuxer = new MediaMuxer(outputPath, OutputFormat.MUXER_OUTPUT_MPEG_4);

        int muxerTrackId = 0;
        int trackId = 0;
        int readExtractors = 0;

        ByteBuffer inputBuffer = ByteBuffer.allocate(500 * 1024);

        int trackNumber = 0;
        for (MediaExtractor mediaExtractor : mediaExtractors)
            trackNumber += mediaExtractor.getTrackCount();

        MediaFormat[] mediaFormats = new MediaFormat[trackNumber];

        for (MediaExtractor extractor : mediaExtractors) {
            for (int j = 0; j < extractor.getTrackCount(); j++) {
                mediaFormats[j] = extractor.getTrackFormat(j);
                // mediaFormats[j].setLong(MediaFormat.KEY_DURATION, mediaFormats[j].getLong(MediaFormat.KEY_DURATION)/2);
                Log.i("mediaFormat " + j, mediaFormats[j].getString(MediaFormat.KEY_MIME));
                mediaMuxer.addTrack(mediaFormats[j]);
            }
        }
        int FrameRate = mediaFormats[0].getInteger(MediaFormat.KEY_FRAME_RATE);

        int KeyFrameDuration = 40000;

        int TimeSkip = (FrameRate * 2) * KeyFrameDuration;

        long skippedTime = TimeSkip;

        long outPutDuration = mediaFormats[0].getLong(MediaFormat.KEY_DURATION) - skippedTime;

        mediaMuxer.start();

        for (MediaExtractor mediaExtractor : mediaExtractors) mediaExtractor.selectTrack(0);

        long presentationTimeUs = mediaExtractors[readExtractors].getSampleTime();

        while (true) {
            if (muxerTrackId == 0) presentationTimeUs += KeyFrameDuration;
            else presentationTimeUs = mediaExtractors[readExtractors].getSampleTime();

            float progress = ((float) presentationTimeUs / outPutDuration) * 100;

            int bufferSize = mediaExtractors[readExtractors].readSampleData(inputBuffer, 0);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            bufferInfo.set(0, bufferSize, presentationTimeUs,
                    mediaExtractors[readExtractors].getSampleFlags());

            Log.i("Time",
                    +progress + "%"
                            + " presentationTimeUs: " + bufferInfo.presentationTimeUs
                            + " mime:" + mediaFormats[muxerTrackId].getString(MediaFormat.KEY_MIME)
                            + " flags " + bufferInfo.flags
                            + " outPutDuration: " + outPutDuration
                            + " bufferSize: " + bufferInfo.size);

            if (bufferSize > 0 && progress < 100) {

                mediaMuxer.writeSampleData(muxerTrackId, inputBuffer, bufferInfo);

                long timeBefore = mediaExtractors[readExtractors].getSampleTime();

                long timeToSkip = outPutDuration / 2;


                if (timeBefore >= timeToSkip && timeBefore <= timeToSkip + KeyFrameDuration) {
                    Log.i("TAG", "encode: skip " + TimeSkip);
                    mediaExtractors[readExtractors].seekTo(timeBefore + TimeSkip, MediaExtractor.SEEK_TO_NEXT_SYNC);
                } else mediaExtractors[readExtractors].advance();


                long time_after = mediaExtractors[readExtractors].getSampleTime();

                Log.i("SampleTime", time_after - timeBefore + " microns");
            } else if (progress != 0) {
                trackId++;
                muxerTrackId++;

                if (trackId >= trackNumber) {
                    mediaExtractors[readExtractors].release();
                    readExtractors++;

                    if (!(readExtractors < mediaExtractors.length)) break;

                    trackId = 0;
                    trackNumber = mediaExtractors[readExtractors].getTrackCount();
                }
                mediaExtractors[readExtractors].selectTrack(trackId);
            }
        }

        mediaMuxer.stop();
        mediaMuxer.release();

        return true;
    }
}
