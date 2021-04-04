package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class CodecManager {
    interface IdListener {
        void onIdAvailable(int Id);
    }

    static class CodecManagerResult {
        ByteBuffer OutputBuffer;
        MediaCodec.BufferInfo bufferInfo;

        public CodecManagerResult(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo) {
            OutputBuffer = outputBuffer;
            this.bufferInfo = bufferInfo;
        }

    }//todo simplifly ?

    public interface ProcessListener {
        void OnProceed(CodecManagerResult decoderResult);
    }

    public static class CodecManagerRequest {
        public final int BufferId;
        public final MediaCodec.BufferInfo bufferInfo;
        ProcessListener ProcessListener;

        public CodecManagerRequest(int bufferId, MediaCodec.BufferInfo bufferInfo, CodecManager.ProcessListener processListener) {
            BufferId = bufferId;
            this.bufferInfo = bufferInfo;
            ProcessListener = processListener;
        }
    }

    public static class OutputPromise extends CodecManagerRequest {

        public OutputPromise(int bufferId, MediaCodec.BufferInfo bufferInfo, CodecManager.ProcessListener processListener) {
            super(bufferId, bufferInfo, processListener);
        }
    }

    private final MediaCodec Codec;
    public MediaFormat MediaFormat;

    public final ArrayList<Integer> InputIds = new ArrayList<>();
    public final ArrayList<IdListener> InputIDListeners = new ArrayList<>();
    public final ArrayList<CodecManagerRequest> InputPromises = new ArrayList<>();
    public final ArrayList<CodecManagerRequest> OutputPromises = new ArrayList<>();

    public CodecManagerRequest getOutputPromise(int BufferId) {
        for (int i = 0; i < OutputPromises.size(); i++) {
            if (OutputPromises.get(i).BufferId == BufferId) {
                CodecManagerRequest codecManagerRequest = OutputPromises.get(i);
                OutputPromises.remove(i);
                return codecManagerRequest;
            }
        }
        return null;
    }

    public CodecManager(MediaCodec mediaCodec, MediaFormat mediaFormat) {
        // todo adicionar multidetherd ou loding
        Codec = mediaCodec;
        MediaFormat = mediaFormat;
        Codec.setCallback(new MediaCodec.Callback() {

            @Override
            public void onInputBufferAvailable(@NonNull final MediaCodec mediaCodec,
                                               final int inputBufferId) {
                if (InputIDListeners.size() != 0) {
                    IdListener idListener = InputIDListeners.get(0);
                    idListener.onIdAvailable(inputBufferId);
                    InputIDListeners.remove(idListener);
                } else {
                    InputIds.add(inputBufferId);
                }
            }

            @Override
            public void onOutputBufferAvailable(@NonNull final MediaCodec mediaCodec,
                                                final int outputBufferId,
                                                @NonNull final MediaCodec.BufferInfo bufferInfo) {
                CodecManagerRequest promise = getOutputPromise(outputBufferId);
                if (promise == null) {
                    Log.e("OnOutputAvailable", "not are premises bur has a output ?");
                    return;
                }
                promise.ProcessListener.OnProceed(new CodecManagerResult(Codec.getOutputBuffer(outputBufferId),bufferInfo));
                Codec.releaseOutputBuffer(outputBufferId, false);
            }

            @Override
            public void onError(@NonNull final MediaCodec mediaCodec,
                                @NonNull final MediaCodec.CodecException e) {
                Log.e("MediaCodecERROR", "onError: ", e);
            }

            @Override
            public void onOutputFormatChanged(@NonNull final MediaCodec mediaCodec,
                                              @NonNull final MediaFormat mediaFormat) {
            }
        });
        Codec.configure(mediaFormat, null, null, 0);
        Codec.start();
    }

    public void getInputBufferId(IdListener idListener) {
        if (InputIds.size() > 0) {
            int InputId = InputIds.get(0);
            InputIds.remove(0);
            idListener.onIdAvailable(InputId);
        } else {
            InputIDListeners.add(idListener);
        }
    }

    public ByteBuffer getInputBuffer(int inputID) {
        return Codec.getInputBuffer(inputID);
    }

    public void processInput(CodecManagerRequest codecManagerRequest) {
        OutputPromises.add(codecManagerRequest);
        Codec.queueInputBuffer(codecManagerRequest.BufferId,
                codecManagerRequest.bufferInfo.offset,
                codecManagerRequest.bufferInfo.size,
                codecManagerRequest.bufferInfo.presentationTimeUs,
                codecManagerRequest.bufferInfo.flags);
    }
}
