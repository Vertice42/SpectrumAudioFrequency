package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;

import com.example.spectrumaudiofrequency.BuildConfig;
import com.example.spectrumaudiofrequency.core.ByteQueue;

public class EncoderCodecManager extends CodecManager {
    private final ByteQueue byteQueue;

    private long PresentationTimeUs = 0;

    public EncoderCodecManager(MediaFormat mediaFormat) {
        super(mediaFormat, false);
        this.SampleSize = getInputBufferLimit();
        byteQueue = new ByteQueue(this.SampleSize * 1000);
    }

    public void setSampleDuration(int SampleDuration) {
        assert !BuildConfig.DEBUG || SampleDuration != 0;
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

    public void putData(int InputBufferId, boolean LastSample, byte[] data) {
        BufferInfo bufferInfo = new BufferInfo();
        bufferInfo.set(0, data.length, PresentationTimeUs, MediaCodec.BUFFER_FLAG_KEY_FRAME);

        if (LastSample) {
            double bytesDuration = SampleDuration / (double) data.length;
            PresentationTimeUs += data.length * bytesDuration;
        } else {
            PresentationTimeUs += SampleDuration;
        }
        putAndProcessInput(InputBufferId, data, bufferInfo);
    }
    private void addPutInputRequest(boolean LastSample, byte[] data) {
        this.addInputIdRequest(InputID -> putData(InputID, LastSample, data));
    }

    public MediaFormat getOutputFormat() {
        return super.getOutputFormat();
    }
}