package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;

import com.example.spectrumaudiofrequency.BuildConfig;
import com.example.spectrumaudiofrequency.core.ByteQueue;

import java.util.LinkedList;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;

public class EncoderCodecManager extends CodecManager {
    private final LinkedList<ResultPromiseListener> onEncodeListeners = new LinkedList<>();
    private ByteQueue byteQueue;

    private long PresentationTimeUs = 0;
    private double ByteDuration;

    public EncoderCodecManager(MediaFormat mediaFormat) {
        super.prepare(mediaFormat, false);
    }

    public void setSampleMetrics(SampleMetrics sampleMetrics) {
        if (BuildConfig.DEBUG) {
            if (sampleMetrics.SampleDuration == 0 || sampleMetrics.SampleSize == 0)
                throw new AssertionError();
        }

        this.SampleDuration = sampleMetrics.SampleDuration;
        this.SampleSize = sampleMetrics.SampleSize;
        this.ByteDuration = sampleMetrics.getByteDuration();
        byteQueue = new ByteQueue(this.SampleSize * 10);
    }

    public void putData(int InputBufferId, boolean LastSample, byte[] data) {
        BufferInfo bufferInfo = new BufferInfo();
        bufferInfo.set(0, data.length, PresentationTimeUs, 0);

        if (LastSample) {
            super.close();
            bufferInfo.flags = BUFFER_FLAG_END_OF_STREAM;
        }
        putAndProcessInput(InputBufferId, data, bufferInfo, this::executeOnEncodeListeners);
        PresentationTimeUs += data.length * ByteDuration;
    }

    private void addPutInputRequest(boolean LastSample, byte[] data) {
        this.addInputIdRequest(InputID -> putData(InputID, LastSample, data));
    }

    public void addPutInputRequest(byte[] data) {
        byteQueue.put(data);
        int inputBufferLimit = this.getInputBufferLimit();
        if (byteQueue.getSize() >= inputBufferLimit) {
            while (byteQueue.getSize() >= inputBufferLimit) {
                byte[] bytes = byteQueue.pollList(inputBufferLimit);
                addPutInputRequest(false, bytes);
            }
        }
    }

    private void putAllBytesToEncode(int inputBufferLimit) {
        while (true) {
            int queueSize = byteQueue.getSize();
            if (queueSize <= inputBufferLimit) {
                byte[] bytes = byteQueue.pollList(queueSize);
                addPutInputRequest(true, bytes);
                break;
            } else {
                byte[] bytes = byteQueue.pollList(inputBufferLimit);
                addPutInputRequest(false, bytes);
            }
        }
    }

    public void addLastPutInputRequest(byte[] data) {
        byteQueue.put(data);
        addLastPutInputRequest();
    }

    public void addLastPutInputRequest() {
        super.close();
        putAllBytesToEncode(this.getInputBufferLimit());
    }

    public void addOnEncode(ResultPromiseListener resultPromiseListener) {
        onEncodeListeners.add(resultPromiseListener);
    }

    private void executeOnEncodeListeners(CodecSample codecSample) {
        for (int i = 0; i < onEncodeListeners.size(); i++) {
            onEncodeListeners.get(i).onKeep(codecSample);
        }
    }

    public MediaFormat getOutputFormat() {
        return super.getOutputFormat();
    }
}