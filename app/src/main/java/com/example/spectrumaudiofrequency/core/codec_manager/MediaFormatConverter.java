package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaFormatConverter {
    private final MediaExtractor ExtractorOriginalMedia;
    private final String OutputMediaName;

    public MediaFormatConverter(String OriginalMediaPath, String OutputMediaName) {
        ExtractorOriginalMedia = new MediaExtractor();
        try {
            ExtractorOriginalMedia.setDataSource(OriginalMediaPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.OutputMediaName = OutputMediaName;

    }

    public void convert() {
        MediaCodec codec = null;
        try {
            codec = MediaCodec.createByCodecName(OutputMediaName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat originalFormat = ExtractorOriginalMedia.getTrackFormat(0);

        codec.configure(originalFormat, null, null, 0);

        MediaFormat outputFormat = codec.getOutputFormat();

        long timeoutUs = 0;

        codec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {

            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {

            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {

            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {

            }
        });

        codec.start();

        while (true) {
            int inputBufferId = codec.dequeueInputBuffer(timeoutUs);
            if (inputBufferId >= 0) {
                ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferId);

                int size = ExtractorOriginalMedia.readSampleData(inputBuffer, 0);
                long presentationTimeUs = ExtractorOriginalMedia.getSampleTime();
                int flags = ExtractorOriginalMedia.getSampleFlags();

                codec.queueInputBuffer(inputBufferId, 0, size, presentationTimeUs, flags);
            }

        codec.stop();
        codec.release();
    }
    }
}
