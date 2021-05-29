package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;

import com.example.spectrumaudiofrequency.core.ByteQueue;

import java.util.ArrayList;

public class EncoderCodecManager extends CodecManager {
    private final ArrayList<ResultPromiseListener> ResultsPromises = new ArrayList<>();
    private final ByteQueue byteQueue = new ByteQueue(1024 * 5000);

    private final int SampleSize;
    private int SampleDuration;
    private long PresentationTimeUs = 0;

    public EncoderCodecManager(MediaFormat mediaFormat) {
        super(mediaFormat, false);
        this.SampleSize = getInputBufferLimit();
    }

    public void setSampleDuration(int sampleDuration) {
        SampleDuration = sampleDuration;
    }

    public synchronized void addPutInputRequest(byte[] data) {
        byteQueue.put(data);
        int inputBufferLimit = this.getInputBufferLimit();
        if (byteQueue.size() >= inputBufferLimit) {
            byte[] bytes = byteQueue.pollList(inputBufferLimit);
            addInputBufferIdRequest(false, bytes);
        } else if (IsStopped) {
            while (byteQueue.size() > 0) {
                int size = byteQueue.size();
                if (size >= inputBufferLimit) size = inputBufferLimit;
                byte[] bytes = byteQueue.pollList(inputBufferLimit);
                addInputBufferIdRequest(true, bytes);
            }
        }
    }

    private void keepResultPromises(CodecSample codecSample) {
        for (int i = 0; i < ResultsPromises.size(); i++)
            ResultsPromises.get(i).onKeep(codecSample);
    }

    public void putData(int InputBufferId, boolean LastSample, byte[] data) {
        if (byteQueue.size() >= getInputBufferLimit() || LastSample) {
            BufferInfo bufferInfo = new BufferInfo();
            bufferInfo.set(0, SampleSize, PresentationTimeUs, 0);
            addOrderlyOutputPromise(new OutputPromise(bufferInfo.presentationTimeUs,
                    this::keepResultPromises));

            if (LastSample) {
                double bytesDuration = SampleDuration / (double) SampleSize;
                PresentationTimeUs += data.length * bytesDuration;
            } else {
                PresentationTimeUs += SampleDuration;
            }
            getInputBuffer(InputBufferId).put(data);
            processInput(new CodecManagerRequest(InputBufferId, bufferInfo));
        } else {
            GiveBackInputID(InputBufferId);
        }
    }

    private void addInputBufferIdRequest(boolean LastSample, byte[] data) {
        this.addInputIdRequest(InputID -> putData(InputID, LastSample, data));
    }

    public void addEncoderOutputPromise(ResultPromiseListener resultPromiseListener) {
        ResultsPromises.add(resultPromiseListener);
    }

    public void removeEncoderOutputPromise(ResultPromiseListener resultPromiseListener) {
        ResultsPromises.remove(resultPromiseListener);
    }

    public int getEncoderPromisesSize() {
        return ResultsPromises.size();
    }

    public MediaFormat getOutputFormat() {
        return super.getOutputFormat();
    }
}