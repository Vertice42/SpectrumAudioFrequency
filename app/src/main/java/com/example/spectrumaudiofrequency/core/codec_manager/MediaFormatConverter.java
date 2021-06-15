package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaFormat;

import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.OnOutputListener;
import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.SampleMetrics;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.DecoderResult;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.PeriodRequest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.spectrumaudiofrequency.sinusoid_converter.MixSample.Mix;

public class MediaFormatConverter {
    private final DecoderManagerWithStorage[] decoders;
    private final MediaFormat NewMediaFormat;
    private EncoderCodecManager encoder;
    private MediaFormatConverterFinishListener FinishListener;
    private MediaFormatConverterListener ConverterListener;
    private boolean IsStarted = false;
    private long MediaDuration = 0;
    private int SampleSize;

    public MediaFormatConverter(Context context, int[] MediaIds, MediaFormat newMediaFormat) {
        //int sampleRate = newMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        //newMediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE,  sampleRate);

        decoders = new DecoderManagerWithStorage[MediaIds.length];
        for (int i = 0; i < decoders.length; i++) {
            decoders[i] = new DecoderManagerWithStorage(context, MediaIds[i]);
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
        for (DecoderManagerWithStorage decoder : decoders)
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

    private synchronized void putNextSampleToEncode(DecoderManagerWithStorage[] decoders,
                                                    AtomicInteger RequiredSampleId,
                                                    int BiggerNumberSamples) {

        RequiredSampleId.getAndIncrement();
        if (RequiredSampleId.get() > BiggerNumberSamples) {
            awaitingAllDecodersFinish();
            BiggerNumberSamples = getBiggerNumberSamples();
            if (RequiredSampleId.get() > BiggerNumberSamples) {
                encoder.stop();
                return;
            }
        }

        for (DecoderManagerWithStorage decoder : decoders) {
            decoder.makeRequest(new PeriodRequest(RequiredSampleId.get(), decoderResult ->
                    encoder.addPutInputRequest(decoderResult.bytes)));
        }
    }

    private void makeInputRequests(DecoderManagerWithStorage[] decoders,
                                   AtomicInteger RequiredSampleId) {
        int interactions = encoder.getNumberOfInputsIdsAvailable();
        if (interactions == 0) interactions = 1;
        for (int i = 0; i < interactions; i++)
            putNextSampleToEncode(decoders, RequiredSampleId, getBiggerNumberSamples());
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

    private int getBiggerNumberSamples() {
        int BiggerNumberSamples = decoders[0].getNumberOfSamples();
        for (DecoderManagerWithStorage decoderManager : decoders) {
            int numberOfSamples = decoderManager.getNumberOfSamples();
            if (numberOfSamples > BiggerNumberSamples) BiggerNumberSamples = numberOfSamples;
        }
        return BiggerNumberSamples;
    }

    private int getBiggerSampleDuration(DecoderManagerWithStorage[] decoders) {
        int BiggerSampleDuration = 0;
        for (DecoderManagerWithStorage decoder : decoders) {
            SampleMetrics sampleMetrics = decoder.getSampleMetrics();
            if (sampleMetrics.SampleDuration > BiggerSampleDuration)
                BiggerSampleDuration = sampleMetrics.SampleDuration;
        }
        return BiggerSampleDuration;
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
            for (DecoderManagerWithStorage decoderManager : decoders) {
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
            AtomicReference<OnOutputListener> onFirstSample = new AtomicReference<>();
            CountDownLatch awaitFirst = new CountDownLatch(1);
            onFirstSample.set(codecSample -> {
                IsStarted = true;
                awaitFirst.countDown();
                encoder.removeOnOutputListener(onFirstSample.get());
            });
            encoder.addOnOutputListener(onFirstSample.get());
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
        encoder.addOnOutputListener(encoderResult -> ConverterListener.onConvert(encoderResult));
        encoder.addOnFinishListener(FinishListener::OnFinish);
        for (DecoderManagerWithStorage decoder : decoders) decoder.start();

        //int biggerNumberSamples = getBiggerNumberSamples();
        //int lastPeriod = biggerNumberSamples - 1;
        int ChannelsNumber = decoders[0].ChannelsNumber;
        encoder.setSampleDuration(getBiggerSampleDuration(decoders));

        AtomicInteger RequiredSampleId = new AtomicInteger();

        makeInputRequests(decoders, RequiredSampleId);
        encoder.addOnInputIdAvailableListener(() -> makeInputRequests(decoders, RequiredSampleId));
    }

    public void pause() {
        for (DecoderManagerWithStorage decoder : decoders) decoder.pause();
    }

    public void restart() {
        for (DecoderManagerWithStorage decoder : decoders) decoder.start();
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
