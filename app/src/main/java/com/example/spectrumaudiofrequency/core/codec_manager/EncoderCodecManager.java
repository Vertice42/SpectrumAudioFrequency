package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.nio.ByteBuffer;

public class EncoderCodecManager extends CodecManager{
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static class CodecRequest {
        MediaCodec.BufferInfo bufferInfo;
        OutputListenerCustum OutputListenerCustum;

        public CodecRequest(MediaCodec.BufferInfo bufferInfo,
                            OutputListenerCustum outputListenerCustum) {
            this.bufferInfo = bufferInfo;
            OutputListenerCustum = outputListenerCustum;
        }
    }

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