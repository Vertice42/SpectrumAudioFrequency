package com.example.spectrumaudiofrequency.core.codec_manager;

import com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoder.DecoderResult;

import java.util.LinkedList;


public class SortedDecoderResultQueue extends LinkedList<DecoderResult> {
    public boolean add(DecoderResult decoderResult) {
        int i = 0;
        while (i < this.size()) {
            if (decoderResult.bufferInfo.size > this.get(i).bufferInfo.size) i++;
            else break;
        }
        this.add(i, decoderResult);
        return true;
    }
}