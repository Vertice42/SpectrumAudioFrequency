package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaFormat;
import android.util.Log;

import com.example.spectrumaudiofrequency.util.CalculatePerformance;

import static com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.DecoderResult.joiningSampleChannels;
import static com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.DecoderResult.separateSampleChannels;
import static com.example.spectrumaudiofrequency.sinusoid_converter.SamplingResize.ResizeSampling;

public class MediaFormatConverter {
    private final DecoderManager decoder;
    private final EncoderCodecManager encoder;
    private MediaFormatConverterFinishListener FinishListener;
    private MediaFormatConverterListener ConverterListener;

    public MediaFormatConverter(Context context, int MediaToConvertId, MediaFormat newMediaFormat) {
        decoder = new DecoderManager(context, MediaToConvertId);
        newMediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, newMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) / 2);
        //newMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, newMediaFormat.getInteger(MediaFormat.KEY_BIT_RATE) / 2);
        encoder = new EncoderCodecManager(newMediaFormat);
    }

    public void start() throws InterruptedException {
        decoder.addOnDecodeListener(decoderResult -> {
            CalculatePerformance.LogPercentage("Decoder ",
                    decoderResult.bufferInfo.presentationTimeUs,
                    decoder.getTrueMediaDuration());

            int channelsNumber = decoder.ChannelsNumber;

            short[][] sampleChannels = separateSampleChannels(decoderResult.bytes, channelsNumber);
            short[][] resizedSamples = new short[sampleChannels.length][];
            int NewSize = sampleChannels[0].length / 2;
            for (int i = 0; i < sampleChannels.length; i++) {
                resizedSamples[i] = ResizeSampling(sampleChannels[i], NewSize);
            }
            byte[] bytes = joiningSampleChannels(resizedSamples, channelsNumber);
            decoderResult.bufferInfo.size = bytes.length;

            encoder.addPutInputRequest(decoderResult.bufferInfo, bytes);
            // encoder.addPutInputRequest(decoderResult.bufferInfo,decoderResult.bytes);

        });

        decoder.addOnFinishListener(encoder::stop);
        //decoder.addOnFinishListener(() -> Log.i("OnFinish", "OnFinish: "));
        encoder.addEncoderListener(encoderResult -> {
            try {
                Log.i("limit", "" + encoder.getInputBufferLimit());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            CalculatePerformance.LogPercentage("Encoder ",
                    encoderResult.bufferInfo.presentationTimeUs,
                    encoder.MediaDuration);
            ConverterListener.onConvert(encoderResult);
        });
        encoder.addOnFinishListener(() -> FinishListener.OnFinish());

        decoder.setNewSampleSize(encoder.getInputBufferLimit());
        decoder.startDecoding();
    }

    public void setOnConvert(MediaFormatConverterListener onConvert) {
        ConverterListener = onConvert;
    }

    public void setFinishListener(MediaFormatConverterFinishListener finishListener) {
        this.FinishListener = finishListener;
    }

    public long TrueMediaDuration() {
        return decoder.getTrueMediaDuration();
    }

    public MediaFormat getOutputFormat() {
        return encoder.getOutputFormat();
    }

    public interface MediaFormatConverterFinishListener {
        void OnFinish();
    }

    public interface MediaFormatConverterListener {
        void onConvert(CodecManager.CodecSample codecSample);
    }
}
