package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaFormat;

import com.example.spectrumaudiofrequency.util.CalculatePerformance;

public class MediaFormatConverter {
    private final DecoderManager decoder;
    private final EncoderCodecManager encoder;
    private MediaFormatConverterFinishListener FinishListener;
    private MediaFormatConverterListener ConverterListener;

    public MediaFormatConverter(Context context, int MediaToConvertId, MediaFormat newMediaFormat) {
        decoder = new DecoderManager(context, MediaToConvertId);
        encoder = new EncoderCodecManager(newMediaFormat);
    }

    public void start() throws InterruptedException {
        decoder.addOnDecodeListener(decoderResult -> {
            CalculatePerformance.LogPercentage("Decoder ",
                    decoderResult.bufferInfo.presentationTimeUs,
                    decoder.TrueMediaDuration());
            encoder.addInputIdRequest(InputID ->
                    encoder.putData(InputID, decoderResult.bufferInfo, decoderResult.bytes));
        });

        decoder.addOnFinishListener(encoder::stop);
        encoder.addOutputListener(encoderResult -> {
            CalculatePerformance.LogPercentage("Decoder ",
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
        return decoder.TrueMediaDuration();
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
