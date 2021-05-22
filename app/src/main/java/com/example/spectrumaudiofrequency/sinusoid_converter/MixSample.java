package com.example.spectrumaudiofrequency.sinusoid_converter;

import android.util.Log;

import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.CodecSample;

import static com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.DecoderResult.SampleChannelsToBytes;
import static com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.DecoderResult.separateSampleChannels;
import static com.example.spectrumaudiofrequency.sinusoid_converter.SamplingResize.ResizeSamplesChannels;

public class MixSample {
    static private short[][] mix(short[][] a, short[][] b) {
        short[][] c = new short[a.length][];
        for (int i = 0; i < c.length; i++) {
            c[i] = new short[a[i].length];
            for (int j = 0; j < c[i].length; j++) c[i][j] = (short) ((a[i][j] + b[i][j]) / 2);
        }
        return c;
    }

    public static byte[] Mix(CodecSample[] codecSamples, int ChannelsNumber, int SampleSize) {
        short[][] result = separateSampleChannels(codecSamples[0].bytes, ChannelsNumber);
        if (result[0].length != SampleSize) {
            Log.i("SampleSizeDif", result[0].length + " != " + SampleSize);
            result = ResizeSamplesChannels(result, SampleSize);
        }
        for (int i = 1; i < codecSamples.length; i++) {
            if (codecSamples[i].bytes != null) {
                Log.i("codecSamplesSize[" + i + ']', "" + codecSamples[i].bytes.length);
                short[][] next = separateSampleChannels(codecSamples[i].bytes, ChannelsNumber);
                if (next[i].length != SampleSize) {
                    Log.i("SampleSizeDif", next[i].length + " != " + SampleSize);
                    next = ResizeSamplesChannels(next, SampleSize);
                }

                result = mix(result, next);
            }
        }

        return SampleChannelsToBytes(result, ChannelsNumber);
    }

}
