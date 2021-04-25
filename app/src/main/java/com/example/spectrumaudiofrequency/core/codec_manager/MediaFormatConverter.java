package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaFormat;
import android.util.Log;

import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.CodecManagerResult;
import com.example.spectrumaudiofrequency.core.codec_manager.EncoderCodecManager.InputBufferListener;

import java.util.concurrent.CountDownLatch;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;
import static com.example.spectrumaudiofrequency.util.Math.CalculatePercentage;

public class MediaFormatConverter {
    public interface MediaFormatConverterListener {
        void onConvert(boolean end, CodecManagerResult codecManagerResult
        );
    }

    private MediaFormatConverterListener mediaFormatConverterListener;
    private CountDownLatch countDownLatch = new CountDownLatch(1);

    private final DecoderCodecWithCacheManager decoder;
    private final EncoderCodecManager encoderCodecManager;

    public MediaFormatConverter(Context context, int MediaToConvertId, MediaFormat newMediaFormat) {
        decoder = new DecoderCodecWithCacheManager(context, MediaToConvertId);
        encoderCodecManager = new EncoderCodecManager(newMediaFormat);
    }

    public void setOnConvert(MediaFormatConverterListener listener) {
        mediaFormatConverterListener = listener;
    }

    private void LogPercentage(CodecManagerResult codecResult, String Type) {
        Log.i("On Converter", Type + " Process: " + CalculatePercentage(codecResult.
                bufferInfo.presentationTimeUs, decoder.TrueMediaDuration()) + "% " +
                "presentationTimeUs " + codecResult.bufferInfo.presentationTimeUs +
                " MediaDuration: " + decoder.TrueMediaDuration());
    }

    private void convert(int SampleId) {
        decoder.addRequest(new DecoderCodecWithCacheManager.PeriodRequest(SampleId,
                decoderResult -> {
                    // LogPercentage(decoderResult, "Decoder");
                    if (countDownLatch != null) countDownLatch.countDown();
                    InputBufferListener onInputObtained = (bufferId, inputBuffer) -> {
                        Log.i("data length", "inputBuffer limit " + inputBuffer.limit() +
                                " decoder length:" + decoderResult.Sample.length);
                        byte[] bytes = new byte[inputBuffer.limit()];

                        for (int i = 0; i < bytes.length && i < decoderResult.Sample.length; i++) {
                            bytes[i] = decoderResult.Sample[i];
                        }

                        inputBuffer.put(bytes);
                        int sampleId = SampleId;
                        sampleId++;

                        long lastPeace = decoder.getSampleLength();
                        final boolean End = sampleId > lastPeace;
                        Log.i("SampleId", "" + sampleId + " lastPeace:" + lastPeace);

                        decoderResult.bufferInfo.size = bytes.length;
                        decoderResult.bufferInfo.flags = BUFFER_FLAG_KEY_FRAME;
                        if (End) decoderResult.bufferInfo.flags = BUFFER_FLAG_END_OF_STREAM;
                        encoderCodecManager.processInput(bufferId, new EncoderCodecManager
                                .CodecRequest(decoderResult.bufferInfo,
                                encoderResult -> {
                                    //LogPercentage(encoderResult, "Encoder");
                                    mediaFormatConverterListener.onConvert(End,
                                            new CodecManagerResult(encoderResult.OutputBuffer,
                                                    encoderResult.bufferInfo));
                                }));
                        if (!End) convert(sampleId);
                    };
                    encoderCodecManager.getInputBuffer(onInputObtained);
                }));
    }

    public void start() {
        convert(0);
    }

    public MediaFormat getOutputFormat() throws InterruptedException {
        if (countDownLatch != null) {
            countDownLatch.await();
            countDownLatch = null;
        }
        return encoderCodecManager.getOutputFormat();
    }
}
