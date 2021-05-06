package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class EncoderCodecManager extends CodecManager {
    ArrayList<PromiseResultListener> EncoderResultsListeners = new ArrayList<>();

    public EncoderCodecManager(MediaFormat mediaFormat) {
        super(mediaFormat, false);
    }

    @Override
    public void NextPeace() {
    }

    public void getInputBufferID(IdListener idListener) {
        super.getInputBufferID(idListener);
    }

    private synchronized void executeEncoderResultsListeners(CodecSample coderResult) {
        for (int i = 0; i < EncoderResultsListeners.size(); i++) {
            EncoderResultsListeners.get(i).onKeep(coderResult);
        }
    }

    public synchronized void putData(int InputID, MediaCodec.BufferInfo bufferInfo, byte[] data) {
        addOrderlyOutputPromise(new OutputOrderPromise(bufferInfo.presentationTimeUs,
                this::executeEncoderResultsListeners));
        ByteBuffer inputBuffer = this.getInputBuffer(InputID);
        inputBuffer.clear();
        inputBuffer.put(data);
        bufferInfo.size = data.length;
        processInput(new CodecManager.CodecManagerRequest(InputID, bufferInfo));
    }

    public void addOutputListener(PromiseResultListener promiseResultListener) {
        EncoderResultsListeners.add(promiseResultListener);
    }

    public void removeOutputListener(PromiseResultListener promiseResultListener) {
        EncoderResultsListeners.remove(promiseResultListener);
    }
}