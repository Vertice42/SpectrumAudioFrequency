package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaFormat;

import java.nio.ByteBuffer;

public class EncoderCodecManager extends CodecManager{

    public EncoderCodecManager(MediaFormat mediaFormat) {
        super(mediaFormat,false);
    }

    public interface InputBufferListener {
        void onAvailable(int bufferId, ByteBuffer byteBuffer);
    }

    public void getInputBuffer(InputBufferListener inputBufferListener) {
        getInputBufferId(bufferId ->
                inputBufferListener.onAvailable(bufferId, getInputBuffer(bufferId)));
    }
}