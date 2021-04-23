package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.CodecManagerRequest;

import java.nio.ByteBuffer;

public class EncoderCodecManager {
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static class CodecRequest {
        MediaCodec.BufferInfo bufferInfo;
        CodecManager.ProcessListener ProcessListener;

        public CodecRequest(MediaCodec.BufferInfo bufferInfo,
                            CodecManager.ProcessListener processListener) {
            this.bufferInfo = bufferInfo;
            ProcessListener = processListener;
        }
    }

    private final CodecManager codecManager;

    public EncoderCodecManager(MediaFormat mediaFormat) {
        codecManager = new CodecManager(mediaFormat, false);
    }

    public interface InputBufferListener {
        void onAvailable(int bufferId, ByteBuffer byteBuffer);
    }

    public MediaFormat getOutputFormat() {
        return codecManager.getOutputFormat();
    }

    public void getInputBuffer(InputBufferListener inputBufferListener) {
        codecManager.getInputBufferId(bufferId -> {
            inputBufferListener.onAvailable(bufferId, codecManager.getInputBuffer(bufferId));
        });
    }

    public void processInput(int bufferId, CodecRequest codecRequest) {
        codecManager.processInput(new CodecManagerRequest(bufferId, codecRequest.bufferInfo,
                codecRequest.ProcessListener));
    }
}