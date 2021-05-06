package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaFormat;
import android.util.Log;

import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.CodecManagerResult;

import java.util.concurrent.atomic.AtomicInteger;

import static com.example.spectrumaudiofrequency.util.Math.CalculatePercentage;

public class MediaFormatConverter {
    public void start() throws InterruptedException {
        AtomicInteger Outputs = new AtomicInteger();
        AtomicInteger inputs = new AtomicInteger();

        decoder.addOnDecodeListener(decoderResult -> {
            inputs.getAndIncrement();
            LogPercentage(decoderResult, "Decoder " + inputs.get());
            encoder.getInputBufferID(InputID ->
                    encoder.putData(InputID, decoderResult.bufferInfo, decoderResult.Sample));
        });

        decoder.addOnFinishListener(encoder::stop);
        encoder.addOutputListener(encoderResult -> ConverterListener.onConvert(encoderResult));
        encoder.addOnFinishListener(() -> FinishListener.OnFinish());

        decoder.setNewSampleSize(encoder.getInputBufferLimit());
        decoder.startDecoding();
    }

    public interface MediaFormatConverterFinishListener {
        void OnFinish();
    }

    private MediaFormatConverterFinishListener FinishListener;
    private MediaFormatConverterListener ConverterListener;

    private final DecoderManager decoder;
    private final EncoderCodecManager encoder;

    public MediaFormatConverter(Context context, int MediaToConvertId, MediaFormat newMediaFormat) {
        decoder = new DecoderManager(context, MediaToConvertId);
        encoder = new EncoderCodecManager(newMediaFormat);
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

    private void LogPercentage(CodecManagerResult codecResult, String Type) {
        Log.i("On Converter", Type + " Process: " + CalculatePercentage(codecResult.
                bufferInfo.presentationTimeUs, decoder.TrueMediaDuration()) + "% " +
                "presentationTimeUs " + codecResult.bufferInfo.presentationTimeUs +
                " MediaDuration: " + decoder.TrueMediaDuration());
    }

    public interface MediaFormatConverterListener {
        void onConvert(CodecManager.CodecSample codecSample);
    }

    public MediaFormat getOutputFormat() {
        return encoder.getOutputFormat();
    }
}
