package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaFormat;

import com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoderWithStorage.PeriodRequest;

import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaFormatConverter {
    private final ExecutorService SingleThreadExecutor;
    protected ArrayList<String> MediasToDecode;
    protected TreeMap<String, MediaDecoderWithStorage> decoders = new TreeMap<>();
    protected MediaFormat NewMediaFormat;
    protected EncoderCodecManager encoder;
    protected Runnable FinishListener;
    protected MediaFormatConverterListener ConverterListener;
    protected SampleHandler sampleHandler;
    protected long MediaDuration;

    protected MediaDecoderWithStorage decoder;
    private boolean IsStarted = false;

    public MediaFormatConverter(MediaDecoderWithStorage decoder, MediaFormat newMediaFormat) {
        this.SingleThreadExecutor = Executors.newSingleThreadExecutor();
        this.decoder = decoder;
        this.MediaDuration = (long) decoder.getTrueMediaDuration();
        this.sampleHandler = this::treatSingleTrack;
        this.NewMediaFormat = newMediaFormat;
    }

    protected MediaFormatConverter(ArrayList<String> MediasToDecode,
                                   TreeMap<String, MediaDecoderWithStorage> decoders,
                                   MediaFormat newMediaFormat) {
        SingleThreadExecutor = Executors.newSingleThreadExecutor();
        this.MediasToDecode = MediasToDecode;
        this.NewMediaFormat = newMediaFormat;
        this.decoders = decoders;
    }

    protected int getLongerSoundTrackSize(short[][][] tracks) {
        int longerSize = 0;
        for (short[][] channels : tracks) {
            if (channels[0].length > longerSize) longerSize = channels[0].length;
        }
        return longerSize;
    }

    protected void putSamplesToEncode(RulerOfHandler samplesRulerOfHandler) {
        int inputsIdsAvailableSize = encoder.getInputsIdsAvailableSize();
        do {
            SingleThreadExecutor.execute(() -> sampleHandler.treat(samplesRulerOfHandler));
            inputsIdsAvailableSize--;
        } while (inputsIdsAvailableSize > 0);
    }

    public void setOnConvert(MediaFormatConverterListener onConvert) {
        ConverterListener = onConvert;
    }

    public void setFinishListener(Runnable finishListener) {
        this.FinishListener = finishListener;
    }

    public long MediaDuration() {
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
        decoder.start();

        encoder = new EncoderCodecManager(NewMediaFormat);
        encoder.setSampleMetrics(decoder.getSampleMetrics());
        encoder.addOnEncode(encoderResult -> ConverterListener.onConvert(encoderResult));
        encoder.addOnFinishListener(FinishListener);

        int samplesNumber = decoder.getNumberOfSamples();

        RulerOfHandler samplesRulerOfHandler = new RulerOfHandler(samplesNumber);
        putSamplesToEncode(samplesRulerOfHandler);
        //todo remover addOnInputIdAvailableListener on stop
        encoder.addOnInputIdAvailableListener(() -> putSamplesToEncode(samplesRulerOfHandler));
    }

    public void pause() {
        decoder.pause();
    }

    public void restart() {
        decoder.restart();
    }

    public void treatSingleTrack(RulerOfHandler samplesRulerOfHandler) {
        final int sampleIndex = samplesRulerOfHandler.SampleIndex();
        if (sampleIndex > samplesRulerOfHandler.LastSampleIndex()) return;

        decoder.makeRequest(new PeriodRequest(sampleIndex, decoderResult -> {
            if (sampleIndex >= samplesRulerOfHandler.LastSampleIndex)
                encoder.addLastPutInputRequest(decoderResult.bytes);
            else encoder.addPutInputRequest(decoderResult.bytes);
        }));

        samplesRulerOfHandler.incrementSampleId();
    }

    protected interface SampleHandler {
        void treat(RulerOfHandler samplesRulerOfHandler);
    }

    public interface MediaFormatConverterListener {
        void onConvert(CodecManager.CodecSample codecSample);
    }

    static class RulerOfHandler {
        int LastSampleIndex;
        int SampleId = 0;

        public RulerOfHandler(int LastSampleIndex) {
            this.LastSampleIndex = LastSampleIndex;
        }

        public void incrementSampleId() {
            SampleId++;
        }

        public int SampleIndex() {
            return SampleId;
        }

        public int LastSampleIndex() {
            return LastSampleIndex;
        }

    }
}
