package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.CodecManagerRequest;
import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.CodecManagerResult;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.example.spectrumaudiofrequency.util.Math.CalculatePercentage;

public class MediaFormatConverter {
    public interface MediaFormatConverterListener {
        void onConvert(CodecManagerResult codecManagerResult);
    }

    public interface MediaFormatConverterFinishListener {
        void OnFinish();
    }

    static class CodecSample {
        MediaCodec.BufferInfo bufferInfo;
        byte[] bytes;

        public CodecSample(MediaCodec.BufferInfo bufferInfo, byte[] bytes) {
            this.bufferInfo = bufferInfo;
            this.bytes = bytes;
        }

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

    public void start() throws InterruptedException {
        AtomicInteger Outputs = new AtomicInteger();
        AtomicInteger inputs = new AtomicInteger();

        decoder.addOnDecodeListener(decoderResult -> {
            inputs.getAndIncrement();
            LogPercentage(decoderResult, "Decoder " + inputs.get());
            encoder.getInputBuffer((bufferId, byteBuffer) -> {
                byteBuffer.put(decoderResult.Sample);
                if (decoderResult.IsLastSample)
                    decoderResult.bufferInfo.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                encoder.processInput(new CodecManagerRequest(bufferId, decoderResult.bufferInfo));
            });
        });


        encoder.addOnOutputListener(encoderResult -> {
            ConverterListener.onConvert(encoderResult);
            if (encoderResult.bufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                FinishListener.OnFinish();
        });

        decoder.setNewSampleSize(encoder.getInputBufferLimit());
        decoder.startDecoding();
    }

    public MediaFormat getOutputFormat() {
        return encoder.getOutputFormat();
    }
}
