package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class EncoderCodecManager extends CodecManager {
    private final ArrayList<ResultPromiseListener> ResultsPromises = new ArrayList<>();

    public EncoderCodecManager(MediaFormat mediaFormat) {
        super(mediaFormat, false);
    }

    @Override
    public void onSampleSorted() {
    }

    public void addPutInputRequest(BufferInfo bufferInfo, byte[] data) {
        super.addInputIdRequest(Id -> putData(Id, bufferInfo, data));
    }

    private synchronized void putData(int InputID, BufferInfo bufferInfo, byte[] data) {
        addOrderlyOutputPromise(new OutputPromise(bufferInfo.presentationTimeUs, codecSample -> {
            for (int i = 0; i < ResultsPromises.size(); i++)
                ResultsPromises.get(i).onKeep(codecSample);
        }));
        ByteBuffer inputBuffer = this.getInputBuffer(InputID);
        inputBuffer.clear();
        inputBuffer.put(data);
        bufferInfo.size = data.length;
        processInput(new CodecManager.CodecManagerRequest(InputID, bufferInfo));
    }

    public void addEncoderListener(ResultPromiseListener resultPromiseListener) {
        ResultsPromises.add(resultPromiseListener);
    }

    public void removeOutputListener(ResultPromiseListener resultPromiseListener) {
        ResultsPromises.remove(resultPromiseListener);
    }

    public int getEncoderPromisesSize() {
        return ResultsPromises.size();
    }
}