package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaFormat;

import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.SampleMetrics;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoder.DecoderResult;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoderWithStorage.PeriodRequest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.spectrumaudiofrequency.sinusoid_converter.MixSample.Mix;

public class MediaFormatConverter {
    private final MediaDecoderWithStorage[] decoders;
    private final MediaFormat NewMediaFormat;
    private EncoderCodecManager encoder;
    private MediaFormatConverterFinishListener FinishListener;
    private MediaFormatConverterListener ConverterListener;
    private boolean IsStarted = false;
    private long MediaDuration = 0;
    private int SampleSize;

    public MediaFormatConverter(Context context, int[] MediaIds, MediaFormat newMediaFormat) {
        decoders = new MediaDecoderWithStorage[MediaIds.length];
        for (int i = 0; i < decoders.length; i++) {
            decoders[i] = new MediaDecoderWithStorage(context, MediaIds[i]);
            /*
            decoders[i].setSampleRearranger(metrics ->
                    new SampleMetrics((metrics.SampleDuration / 2), (int) Math.ceil(((double)
                            metrics.SampleSize * metrics.SampleDuration) /
                            (metrics.SampleDuration / 2f))));
             */
        }

        this.NewMediaFormat = newMediaFormat;
    }

    private void awaitingAllDecodersFinish() {
        int DecodersNotYetFinished = 0;
        for (MediaDecoderWithStorage decoder : decoders)
            if (!decoder.IsCompletelyCodified) DecodersNotYetFinished++;

        if (DecodersNotYetFinished > 0) {
            CountDownLatch awaitingFinish = new CountDownLatch(DecodersNotYetFinished);
            for (int i = 0; i < DecodersNotYetFinished; i++)
                decoders[i].addOnFinishListener(awaitingFinish::countDown);
            try {
                awaitingFinish.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void mix(int Period, int ChannelsNumber, int DecoderID,
                     DecoderResult[] decoderResults, MixResultListener mixResultListener) {
        if (DecoderID < decoders.length) {
            decoders[DecoderID].makeRequest(new PeriodRequest(Period, decoderResult -> {
                decoderResults[DecoderID] = decoderResult;
                mix(Period, ChannelsNumber, (DecoderID + 1), decoderResults, mixResultListener);
            }));
        } else {
            if (SampleSize == 0)
                for (DecoderResult result : decoderResults)
                    SampleSize = (result.bufferInfo.size / (2 + ChannelsNumber));
            mixResultListener.onConverter(Mix(decoderResults, ChannelsNumber, SampleSize));
        }
    }

    private void mixTracks(int Period, int ChannelsNumber, MixResultListener mixResultListener) {
        DecoderResult[] decoderResults = new DecoderResult[decoders.length];
        mix(Period, ChannelsNumber, 0, decoderResults, mixResultListener);
    }

    private synchronized void putNextSampleToEncode(MediaDecoderWithStorage[] decoders,
                                                    AtomicInteger RequiredSampleId,
                                                    AtomicInteger BiggerNumberSamples) {
        final int requiredSampleId = RequiredSampleId.get();
        if (requiredSampleId > BiggerNumberSamples.get()) return;

        for (MediaDecoderWithStorage decoder : decoders) {
            decoder.makeRequest(new PeriodRequest(requiredSampleId, decoderResult -> {
                if (requiredSampleId >= BiggerNumberSamples.get()) {
                    awaitingAllDecodersFinish();
                    BiggerNumberSamples.set(getBiggerNumberSamples());
                    if (requiredSampleId >= BiggerNumberSamples.get()) encoder.setClose();
                }
                encoder.addPutInputRequest(decoderResult.bytes);
            }));
        }

        RequiredSampleId.getAndIncrement();
    }

    private synchronized void putSamplesToEncode(MediaDecoderWithStorage[] decoders,
                                                 AtomicInteger RequiredSampleId,
                                                 AtomicInteger BiggerNumberSamples) {
        int inputsIdsAvailableSize = encoder.getInputsIdsAvailableSize();
        do {
            putNextSampleToEncode(decoders, RequiredSampleId, BiggerNumberSamples);
            inputsIdsAvailableSize--;
        } while (inputsIdsAvailableSize > 0);
    }

    private int getBiggerNumberSamples() {
        int BiggerNumberSamples = decoders[0].getNumberOfSamples();
        for (MediaDecoderWithStorage decoderManager : decoders) {
            int numberOfSamples = decoderManager.getNumberOfSamples();
            if (numberOfSamples > BiggerNumberSamples) BiggerNumberSamples = numberOfSamples;
        }
        return BiggerNumberSamples;
    }

    private SampleMetrics getMetricsWithLongerDuration(MediaDecoderWithStorage[] decoders) {
        SampleMetrics metrics = null;
        for (MediaDecoderWithStorage decoder : decoders) {
            SampleMetrics sampleMetrics = decoder.getSampleMetrics();
            if (metrics == null || sampleMetrics.SampleDuration > metrics.SampleDuration)
                metrics = sampleMetrics;
        }
        return metrics;
    }

    public void setOnConvert(MediaFormatConverterListener onConvert) {
        ConverterListener = onConvert;
    }

    public void setFinishListener(MediaFormatConverterFinishListener finishListener) {
        this.FinishListener = finishListener;
    }

    public long getMediaDuration() {
        if (MediaDuration != 0) return MediaDuration;
        else {
            for (MediaDecoderWithStorage decoderManager : decoders) {
                long trueMediaDuration = (long) decoderManager.getTrueMediaDuration();
                if (trueMediaDuration > MediaDuration) {
                    MediaDuration = trueMediaDuration;
                }
            }
        }
        return MediaDuration;
    }

    public MediaFormat getOutputFormat() {
        if (IsStarted) {
            CountDownLatch awaitFirst = new CountDownLatch(1);
            encoder.addOnReadyListener((SamplesHaveEqualSize, sampleMetrics) -> {
                IsStarted = true;
                awaitFirst.countDown();
            });
            try {
                awaitFirst.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return encoder.getOutputFormat();
    }

    public void start() {
        encoder = new EncoderCodecManager(NewMediaFormat);
        encoder.addOnEncode(encoderResult -> ConverterListener.onConvert(encoderResult));
        encoder.addOnFinishListener(() -> FinishListener.OnFinish());

        for (MediaDecoderWithStorage decoder : decoders) {
            if (!decoder.IsCompletelyCodified) decoder.start();
        }

        //int biggerNumberSamples = getBiggerNumberSamples();
        //int lastPeriod = biggerNumberSamples - 1;
        int ChannelsNumber = decoders[0].ChannelsNumber;

        encoder.setSampleMetrics(getMetricsWithLongerDuration(decoders));

        AtomicInteger RequiredSampleId = new AtomicInteger();
        AtomicInteger BiggerNumberSamples = new AtomicInteger(getBiggerNumberSamples());

        putSamplesToEncode(decoders, RequiredSampleId, BiggerNumberSamples);

        //todo remover addOnInputIdAvailableListener on stop
        encoder.addOnInputIdAvailableListener(() ->
                putSamplesToEncode(decoders, RequiredSampleId, BiggerNumberSamples));
    }

    public void pause() {
        for (MediaDecoderWithStorage decoder : decoders) decoder.pause();
    }

    public void restart() {
        for (MediaDecoderWithStorage decoder : decoders) decoder.start();
    }

    private interface MixResultListener {
        void onConverter(byte[] bytes);
    }

    public interface MediaFormatConverterFinishListener {
        void OnFinish();
    }

    public interface MediaFormatConverterListener {
        void onConvert(CodecManager.CodecSample codecSample);
    }
}
