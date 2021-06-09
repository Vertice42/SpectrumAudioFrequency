package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;

import com.example.spectrumaudiofrequency.BuildConfig;
import com.example.spectrumaudiofrequency.core.ByteQueue;

import java.util.ArrayList;

public class EncoderCodecManager extends CodecManager {
    private final ArrayList<ResultPromiseListener> ResultsPromises = new ArrayList<>();
    private final ByteQueue byteQueue = new ByteQueue(1024 * 5000);

    private long PresentationTimeUs = 0;

    public EncoderCodecManager(MediaFormat mediaFormat) {
        super(mediaFormat, false);
        this.SampleSize = getInputBufferLimit();
    }

    public void setSampleDuration(int SampleDuration) {
        if (BuildConfig.DEBUG) assert SampleDuration != 0;
        this.SampleDuration = SampleDuration;
    }

    public void addPutInputRequest(byte[] data) {
        byteQueue.put(data);
        int inputBufferLimit = this.getInputBufferLimit();
        if (byteQueue.getSize() >= inputBufferLimit) {
            byte[] bytes = byteQueue.pollList(inputBufferLimit);
            addPutInputRequest(false, bytes);
        } else if (IsStopped) {
            while (byteQueue.getSize() > 0) {
                byte[] bytes = byteQueue.pollList(inputBufferLimit);
                addPutInputRequest(true, bytes);
            }
        }
    }

    private void keepResultPromises(CodecSample codecSample) {
        for (int i = 0; i < ResultsPromises.size(); i++) ResultsPromises.get(i).onKeep(codecSample);
    }

    public void putData(int InputBufferId, boolean LastSample, byte[] data) {
        BufferInfo bufferInfo = new BufferInfo();
        bufferInfo.set(0, data.length, PresentationTimeUs, MediaCodec.BUFFER_FLAG_KEY_FRAME);
        addOrderlyOutputPromise(new OutputPromise(bufferInfo.presentationTimeUs,
                this::keepResultPromises));

        if (LastSample) {
            double bytesDuration = SampleDuration / (double) data.length;
            PresentationTimeUs += data.length * bytesDuration;
        } else {
            PresentationTimeUs += SampleDuration;
        }
        getInputBuffer(InputBufferId).put(data);
        processInput(new CodecManagerRequest(InputBufferId, bufferInfo));
    }

    private void addPutInputRequest(boolean LastSample, byte[] data) {
        this.addInputIdRequest(InputID -> putData(InputID, LastSample, data));
    }

    private void addOutputPromise(ResultPromiseListener resultPromiseListener) {
        ResultsPromises.add(resultPromiseListener);
    }

    private void removeEncoderOutputPromise(ResultPromiseListener resultPromiseListener) {
        ResultsPromises.remove(resultPromiseListener);
    }

    public int getEncoderPromisesSize() {
        return ResultsPromises.size();
    }

    public MediaFormat getOutputFormat() {
        return super.getOutputFormat();
    }
}