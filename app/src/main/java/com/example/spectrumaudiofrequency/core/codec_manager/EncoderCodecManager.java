package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;

import com.example.spectrumaudiofrequency.core.ByteQueue;

import java.util.ArrayList;

public class EncoderCodecManager extends CodecManager {
    private final ArrayList<ResultPromiseListener> ResultsPromises = new ArrayList<>();
    private final ByteQueue byteQueue = new ByteQueue();//todo realocasão pode gerar erros já que as amostras na verdade são shorts , com o numeo de canais > 2 > 5 refatorar
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
        byteQueue.add(data);
        if (byteQueue.size() >= this.getInputBufferLimit()) {
            byte[] bytes = byteQueue.peekList(this.getInputBufferLimit());
            putOnBuffer(MediaCodec.BUFFER_FLAG_KEY_FRAME, bytes);
        } else if (IsStopped) {
            while (byteQueue.size() > 0) {
                int flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                byte[] bytes = byteQueue.peekList(this.getInputBufferLimit());
                if (byteQueue.size() == 0) flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                putOnBuffer(flags, bytes);
            }
        }
    }

    private void keepResultPromises(CodecSample codecSample) {
        for (int i = 0; i < ResultsPromises.size(); i++)
            ResultsPromises.get(i).onKeep(codecSample);
    }

    private void putOnBuffer(int Flags, byte[] data) {
        this.addInputIdRequest(InputID -> {
            BufferInfo bufferInfo = new BufferInfo();

            bufferInfo.set(0, SampleSize, PresentationTimeUs, Flags);
            addOrderlyOutputPromise(new OutputPromise(bufferInfo.presentationTimeUs,
                    this::keepResultPromises));

            if (Flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                double bytesDuration = SampleDuration / (double) SampleSize;
                PresentationTimeUs += data.length * bytesDuration;
            } else {
                PresentationTimeUs += SampleDuration;
            }

            getInputBuffer(InputID).put(data);
            processInput(new CodecManagerRequest(InputID, bufferInfo));
        });
    }

    public void addEncoderOutputListener(ResultPromiseListener resultPromiseListener) {
        ResultsPromises.add(resultPromiseListener);
    }

    public void removeEncoderOutputListener(ResultPromiseListener resultPromiseListener) {
        ResultsPromises.remove(resultPromiseListener);
    }

    public int getEncoderPromisesSize() {
        return ResultsPromises.size();
    }
}