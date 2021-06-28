package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaFormat;

import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.SampleMetrics;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoderWithStorage.PeriodRequest;
import com.example.spectrumaudiofrequency.sinusoid_manipulador.SinusoidManipulator;
import com.example.spectrumaudiofrequency.sinusoid_manipulador.SinusoidResize;

import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;

import static com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoder.DecoderResult;
import static com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoder.converterChannelsToBytes;

public class AudioFormatConverter extends MediaFormatConverter {
    private final int ChannelsNumber = 2;//todo criar modo de mixar canaias a mais

    public AudioFormatConverter(ArrayList<String> MediasToDecode,
                                TreeMap<String, MediaDecoderWithStorage> decoders,
                                MediaFormat newMediaFormat) {
        super(MediasToDecode, decoders, newMediaFormat);

        String MediaWithSampleDurationAnchor = "";

        int LargerSampleDuration = 0;
        long LargeMediaDuration = 0;
        for (String mediaName : MediasToDecode) {
            MediaDecoderWithStorage decoder = decoders.get(mediaName);
            assert decoder != null;
            int sampleDuration = decoder.getSampleDuration();
            double mediaDuration = decoder.getTrueMediaDuration();
            if (sampleDuration > LargerSampleDuration) {
                LargerSampleDuration = sampleDuration;
                MediaWithSampleDurationAnchor = mediaName;
            }
            if (mediaDuration > LargeMediaDuration) LargeMediaDuration = (long) mediaDuration;
        }
        final int FINAL_SAMPLE_DURATION = LargerSampleDuration;
        this.MediaDuration = LargeMediaDuration;

        for (String mediaName : MediasToDecode) {
            if (MediaWithSampleDurationAnchor.equals(mediaName)) continue;

            MediaDecoderWithStorage decoder = decoders.get(mediaName);
            assert decoder != null;
            decoder.setSampleRearranger(metrics -> new SampleMetrics(
                    FINAL_SAMPLE_DURATION,
                    (metrics.SampleSize * metrics.SampleDuration / FINAL_SAMPLE_DURATION)));
        }

        sampleHandler = mergeSamples -> {
            final int sampleIndex = mergeSamples.SampleIndex();
            if (sampleIndex > mergeSamples.LastSampleIndex()) return;

            CountDownLatch awaitingRequestResults = new CountDownLatch(MediasToDecode.size());
            ArrayList<DecoderResult> decoderResults = new ArrayList<>();

            for (String mediaName : MediasToDecode) {
                MediaDecoderWithStorage decoder = decoders.get(mediaName);
                assert decoder != null;
                // Log.i("TAG", "make: " + sampleIndex + "/" + mergeSamples.LastSampleIndex() + " ||" + mediaName);
                decoder.makeRequest(new PeriodRequest(sampleIndex, decoderResult -> {
                    if (decoderResult.bytes.length > 0) decoderResults.add(decoderResult);
                    awaitingRequestResults.countDown();
                }));
            }

            try {
                awaitingRequestResults.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (decoderResults.size() == 0) {
                encoder.addLastPutInputRequest();
                return;
            }

            short[][][] tracks = new short[decoderResults.size()][][];

            for (int i = 0; i < decoderResults.size(); i++) {
                DecoderResult decoderResult = decoderResults.get(i);
                tracks[i] = decoderResult.converterBytesToChannels(ChannelsNumber);
            }

            int longerSoundTrackSize = getLongerSoundTrackSize(tracks);

            for (short[][] track : tracks) SinusoidResize.resize(track, longerSoundTrackSize);

            short[][] sinusoidMixed = SinusoidManipulator.mix(tracks);

            byte[] bytes = converterChannelsToBytes(sinusoidMixed);

            if (sampleIndex >= mergeSamples.LastSampleIndex())
                encoder.addLastPutInputRequest(bytes);
            else encoder.addPutInputRequest(bytes);

            {
                int i = 0;
                while (i < MediasToDecode.size()) {
                    String mediaName = MediasToDecode.get(i);
                    MediaDecoderWithStorage decoder = decoders.get(mediaName);
                    assert decoder != null;
                    if (sampleIndex > decoder.getNumberOfSamples()) {
                        MediasToDecode.remove(i);
                    } else i++;
                }
            }

            if (MediasToDecode.size() == 1) {
                this.decoder = this.decoders.get(MediasToDecode.get(0));
                this.decoders.clear();
                this.decoders = null;
                sampleHandler = this::treatSingleTrack;
            }

            mergeSamples.incrementSampleId();
        };
    }

    @Override
    public void start() {
        SampleMetrics metricsWithBigestSampleduration = null;
        for (String mediaName : MediasToDecode) {
            MediaDecoderWithStorage decoder = decoders.get(mediaName);
            assert decoder != null;
            decoder.start();

            SampleMetrics sampleMetrics = decoder.getSampleMetrics();
            if (metricsWithBigestSampleduration == null ||
                    sampleMetrics.SampleDuration > metricsWithBigestSampleduration.SampleDuration)
                metricsWithBigestSampleduration = sampleMetrics;
        }

        encoder = new EncoderCodecManager(NewMediaFormat);
        encoder.setSampleMetrics(metricsWithBigestSampleduration);
        encoder.addOnEncode(encoderResult -> ConverterListener.onConvert(encoderResult));
        encoder.addOnFinishListener(FinishListener);

        int MaxNumberOfSamples = 0;
        for (String mediaName : MediasToDecode) {
            MediaDecoderWithStorage decoder = decoders.get(mediaName);
            assert decoder != null;
            int maxNumberOfSamples = decoder.getNumberOfSamples();
            if (maxNumberOfSamples > MaxNumberOfSamples) MaxNumberOfSamples = maxNumberOfSamples;
        }

        RulerOfHandler samplesRulerOfHandler;
        samplesRulerOfHandler = new RulerOfHandler(MaxNumberOfSamples);
        putSamplesToEncode(samplesRulerOfHandler);

        //todo remover addOnInputIdAvailableListener on stop
        encoder.addOnInputIdAvailableListener(() -> putSamplesToEncode(samplesRulerOfHandler));
    }

    @Override
    public void pause() {
        for (String mediaName : MediasToDecode) {
            MediaDecoderWithStorage decoder = decoders.get(mediaName);
            assert decoder != null;
            decoder.pause();
        }
    }

    @Override
    public void restart() {
        for (String mediaName : MediasToDecode) {
            MediaDecoderWithStorage decoder = decoders.get(mediaName);
            assert decoder != null;
            decoder.restart();
        }
    }
}
