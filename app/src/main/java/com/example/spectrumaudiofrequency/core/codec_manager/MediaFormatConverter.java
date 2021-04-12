package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaFormat;

import com.example.spectrumaudiofrequency.core.ByteQueue;
import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.CodecManagerResult;
import com.example.spectrumaudiofrequency.core.codec_manager.EncoderCodecManager.InputBufferListener;

import java.util.concurrent.CountDownLatch;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;

public class MediaFormatConverter {
    public interface MediaFormatConverterListener {
        void onConvert(boolean end, CodecManagerResult codecManagerResult
        );
    }
    private final long Duration;
    private final ByteQueue Remnant;

    private MediaFormatConverterListener mediaFormatConverterListener;
    private CountDownLatch countDownLatch = new CountDownLatch(1);

    private final DecoderCodecManager decoderCodecManager;
    private final EncoderCodecManager encoderCodecManager;

    public MediaFormatConverter(Context context, int MediaToConvertId, MediaFormat newMediaFormat) {
        this.Duration = newMediaFormat.getLong(MediaFormat.KEY_DURATION);
        decoderCodecManager = new DecoderCodecManager(context, MediaToConvertId);
        encoderCodecManager = new EncoderCodecManager(newMediaFormat);

        Remnant = new ByteQueue();
    }

    public void setOnConvert(MediaFormatConverterListener listener) {
        mediaFormatConverterListener = listener;
    }

    private void convert(long Time) {
        decoderCodecManager.addRequest(new DecoderCodecManager.PeriodRequest(Time,
                decoderResult -> {

                    if (countDownLatch != null) countDownLatch.countDown();

                    Remnant.add(decoderResult.Sample);
                    InputBufferListener onInputObtained = (bufferId, inputBuffer) -> {
                        int byteBufferSize = inputBuffer.limit();

                        byte[] bytes = Remnant.peekList(byteBufferSize);
                        //Log.e("limit", +byteBufferSize + " input length: " + decoderResult.Sample.length);

                        inputBuffer.clear();
                        inputBuffer.put(bytes);

                        long time = Time + decoderCodecManager.SampleDuration;
                        final boolean End = (time >= Duration);

                        decoderResult.bufferInfo.size = bytes.length;

                        if (End) decoderResult.bufferInfo.flags = BUFFER_FLAG_END_OF_STREAM;
                        encoderCodecManager.processInput(bufferId, new EncoderCodecManager
                                .CodecRequest(decoderResult.bufferInfo,
                                encoderResult -> mediaFormatConverterListener.onConvert(End,
                                        new CodecManagerResult(encoderResult.OutputBuffer,
                                                encoderResult.bufferInfo))));
                        if (!End) convert(time);
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
