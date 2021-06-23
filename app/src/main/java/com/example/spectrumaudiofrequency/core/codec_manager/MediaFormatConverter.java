package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaFormat;
import android.util.Log;

import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.SampleMetrics;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoder.DecoderResult;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoderWithStorage.PeriodRequest;
import com.example.spectrumaudiofrequency.sinusoid_manipulador.SinusoidManipulator;
import com.example.spectrumaudiofrequency.sinusoid_manipulador.SinusoidResize;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaFormatConverter {
    private final HashMap<Integer, MediaDecoderWithStorage> decoders = new HashMap<>();
    private final MediaFormat NewMediaFormat;
    private final int ChannelsNumber = 2;//todo criar modo de mixar canaias a mais
    private final ExecutorService SingleThreadExecutor;
    private SamplesFunnel samplesFunnel;
    private int[] MediaIds;
    private EncoderCodecManager encoder;
    private MediaFormatConverterFinishListener FinishListener;
    private MediaFormatConverterListener ConverterListener;
    private boolean IsStarted = false;
    private long MediaDuration = 0;

    public MediaFormatConverter(Context context, int[] mediaIds, MediaFormat newMediaFormat) {
        SingleThreadExecutor = Executors.newSingleThreadExecutor();
        this.MediaIds = mediaIds;
        for (int mediaId : this.MediaIds)
            decoders.put(mediaId, new MediaDecoderWithStorage(context, mediaId));

        if (this.MediaIds.length > 1) {
            int SampleDurationAnchorIndex = 0;
            int LargerSampleDuration = 0;

            for (int mediaId : this.MediaIds) {
                MediaDecoderWithStorage decoder = decoders.get(mediaId);
                assert decoder != null;
                int sampleDuration = decoder.getSampleDuration();
                if (sampleDuration > LargerSampleDuration) {
                    LargerSampleDuration = sampleDuration;
                    SampleDurationAnchorIndex = mediaId;
                }
            }

            for (int mediaId : this.MediaIds) {
                if (SampleDurationAnchorIndex == mediaId) continue;
                MediaDecoderWithStorage decoder = decoders.get(mediaId);
                assert decoder != null;
                int finalLargerSampleDuration = LargerSampleDuration;
                decoder.setSampleRearranger(metrics -> new SampleMetrics(
                        finalLargerSampleDuration,
                        ((metrics.SampleSize * metrics.SampleDuration) / finalLargerSampleDuration)));
            }

            samplesFunnel = samplesFunnelRequest -> {
                final int sampleIndex = samplesFunnelRequest.getSampleIndex();
                if (sampleIndex > samplesFunnelRequest.getLastSampleIndex()) return;

                assert this.MediaIds.length > 0;
                CountDownLatch awaitingRequestResults = new CountDownLatch(this.MediaIds.length);
                LinkedList<DecoderResult> decoderResults = new LinkedList<>();
                ArrayList<Integer> MediaIdsToRemove = makeRequests(sampleIndex,
                        decoderResults,
                        awaitingRequestResults);

                try {
                    awaitingRequestResults.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (decoderResults.size() == 0) {
                    encoder.closeAndStop();
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

                if (sampleIndex >= samplesFunnelRequest.getLastSampleIndex()) {
                    Log.i("TAG", "close");
                    encoder.Close();
                }
                encoder.addPutInputRequest(MediaDecoder.converterChannelsToBytes(sinusoidMixed));

                samplesFunnelRequest.incrementRequiredSampleId();

                for (int i = 0; i < MediaIdsToRemove.size(); i++) {
                    this.MediaIds = removeAndReallocMediaId(MediaIdsToRemove.get(i));
                }
                if (MediaIds.length < 2) samplesFunnel = this::singleTrackFunnel;
            };

        } else {
            samplesFunnel = this::singleTrackFunnel;
        }

        this.NewMediaFormat = newMediaFormat;
    }

    /*
    private void awaitingFinish(MediaDecoderWithStorage decoder) {
        CountDownLatch awaitingFinish = new CountDownLatch(1);
        decoder.addOnDecoderFinishListener(awaitingFinish::countDown);
        try {
            awaitingFinish.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
     */

    @NotNull
    private int[] removeAndReallocMediaId(int decoderIndex) {
        int newSize = decoders.size() - 1;
        if (newSize < 1) return MediaIds;
        int[] NewMediaIds = new int[newSize];
        int mediaIndex = 0;
        int newMediaIndex = 0;
        while (mediaIndex < MediaIds.length) {
            if (MediaIds[mediaIndex] != decoderIndex) {
                NewMediaIds[newMediaIndex] = MediaIds[mediaIndex];
                newMediaIndex++;
            }
            mediaIndex++;
        }
        return NewMediaIds;
    }

    private ArrayList<Integer> makeRequests(int sampleIndex,
                                            LinkedList<DecoderResult> decoderResults,
                                            CountDownLatch awaitingRequestResults) {
        ArrayList<Integer> MediaIdsToRemove = new ArrayList<>();
        for (int mediaId : MediaIds) {
            MediaDecoderWithStorage decoder = decoders.get(mediaId);
            assert decoder != null;
            decoder.makeRequest(new PeriodRequest(sampleIndex, decoderResult -> {
                if (decoderResult.bytes.length > 0) decoderResults.add(decoderResult);
                awaitingRequestResults.countDown();
            }));

            if (sampleIndex > decoder.getSamplesNumber()) MediaIdsToRemove.add(mediaId);
        }

        return MediaIdsToRemove;
    }

    private int getLongerSoundTrackSize(short[][][] tracks) {
        int longerSize = 0;
        for (short[][] channels : tracks) {
            if (channels[0].length > longerSize) longerSize = channels[0].length;
        }
        return longerSize;
    }

    private synchronized void putSamplesToEncode(SamplesFunnelRequest samplesFunnelRequest) {
        int inputsIdsAvailableSize = encoder.getInputsIdsAvailableSize();
        do {
            SingleThreadExecutor.execute(() -> samplesFunnel.putDataInEncode(samplesFunnelRequest));
            inputsIdsAvailableSize--;
        } while (inputsIdsAvailableSize > 0);
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
            for (int mediaId : MediaIds) {
                MediaDecoderWithStorage decoder = decoders.get(mediaId);
                assert decoder != null;
                long trueMediaDuration = (long) decoder.getTrueMediaDuration();
                if (trueMediaDuration > MediaDuration) MediaDuration = trueMediaDuration;
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

        SampleMetrics metrics = null;
        for (int mediaId : MediaIds) {
            MediaDecoderWithStorage decoder = decoders.get(mediaId);
            assert decoder != null;
            decoder.start();

            SampleMetrics sampleMetrics = decoder.getSampleMetrics();
            if (metrics == null || sampleMetrics.SampleDuration > metrics.SampleDuration)
                metrics = sampleMetrics;
        }

        encoder = new EncoderCodecManager(NewMediaFormat);
        encoder.setSampleMetrics(metrics);
        encoder.addOnEncode(encoderResult -> ConverterListener.onConvert(encoderResult));
        encoder.addOnFinishListener(() -> FinishListener.OnFinish());

        HashMap<Integer, Integer> NumberOfSamples = new HashMap<>();
        for (int mediaId : MediaIds) {
            MediaDecoderWithStorage decoder = decoders.get(mediaId);
            assert decoder != null;
            NumberOfSamples.put(mediaId, decoder.getSamplesNumber());
        }

        SamplesFunnelRequest samplesFunnelRequest = new SamplesFunnelRequest(decoders, NumberOfSamples);
        putSamplesToEncode(samplesFunnelRequest);
        //todo remover addOnInputIdAvailableListener on stop
        encoder.addOnInputIdAvailableListener(() -> putSamplesToEncode(samplesFunnelRequest));
    }

    public void pause() {
        for (int mediaId : MediaIds) {
            MediaDecoderWithStorage decoder = decoders.get(mediaId);
            assert decoder != null;
            decoder.pause();
        }
    }

    public void restart() {
        for (int mediaId : MediaIds) {
            MediaDecoderWithStorage decoder = decoders.get(mediaId);
            assert decoder != null;
            decoder.restart();
        }
    }

    private void singleTrackFunnel(SamplesFunnelRequest samplesFunnelRequest) {
        final int SampleIndex = samplesFunnelRequest.getSampleIndex();
        if (SampleIndex > samplesFunnelRequest.getLastSampleIndex()) return;

        MediaDecoderWithStorage decoder = decoders.get(this.MediaIds[0]);
        assert decoder != null;
        decoder.makeRequest(new PeriodRequest(SampleIndex, decoderResult -> {
            if (SampleIndex >= samplesFunnelRequest.LastSampleIndex) encoder.Close();
            encoder.addPutInputRequest(decoderResult.bytes);
        }));

        samplesFunnelRequest.incrementRequiredSampleId();
    }

    private interface SamplesFunnel {
        void putDataInEncode(SamplesFunnelRequest samplesFunnelRequest);
    }

    public interface MediaFormatConverterFinishListener {
        void OnFinish();
    }

    public interface MediaFormatConverterListener {
        void onConvert(CodecManager.CodecSample codecSample);
    }

    static class SamplesFunnelRequest {
        HashMap<Integer, MediaDecoderWithStorage> decoders;
        HashMap<Integer, Integer> NumberSamples;
        int SampleId = 0;
        int LastSampleIndex;

        public SamplesFunnelRequest(HashMap<Integer, MediaDecoderWithStorage> decoders,
                                    HashMap<Integer, Integer> NumberSamples) {
            this.decoders = decoders;
            this.NumberSamples = NumberSamples;
            this.LastSampleIndex = calculateLastSampleIndex();
        }

        public void incrementRequiredSampleId() {
            SampleId++;
        }

        public int getSampleIndex() {
            return SampleId;
        }

        private int calculateLastSampleIndex() {
            Iterator<Map.Entry<Integer, Integer>> iterator = NumberSamples.entrySet().iterator();
            int lastSampleIndex = 0;
            while (iterator.hasNext()) {
                Map.Entry<Integer, Integer> next = iterator.next();
                Integer key = next.getKey();
                Integer numberOfSamples = NumberSamples.get(key);
                assert numberOfSamples != null;
                if (numberOfSamples > lastSampleIndex) lastSampleIndex = numberOfSamples;
            }
            return lastSampleIndex;
        }

        public int getLastSampleIndex() {
            return LastSampleIndex;
        }

        /*
        public void setNumberSamples(int DecoderIndex, int value) {
            NumberSamples.put(DecoderIndex, value);
        }
         */
    }
}
