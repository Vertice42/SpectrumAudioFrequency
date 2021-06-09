package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaFormat;
import android.util.Log;

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
        decoders = new DecoderManagerWithStorage[MediaIds.length];
        for (int i = 0; i < decoders.length; i++)
            decoders[i] = new DecoderManagerWithStorage(context, MediaIds[i], metrics ->
                    new SampleMetrics(metrics.SampleDuration, (int) Math.ceil(((double)
                            metrics.SampleSize * metrics.SampleDuration) / metrics.SampleDuration)));

        /*
        int sampleRate = newMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int bitRate = newMediaFormat.getInteger(MediaFormat.KEY_BIT_RATE);
        Log.i("sampleRate", "" + sampleRate + " bitRate:" + bitRate);
        newMediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, (int) (sampleRate / 2f));
        */

        this.NewMediaFormat = newMediaFormat;
    }

    private void addRequests(int Period, int ChannelsNumber, int DecoderID,
                             DecoderResult[] decoderResults, MixResultListener mixResultListener) {
        if (DecoderID < decoders.length) {
            decoders[DecoderID].makeRequest(new PeriodRequest(Period, decoderResult -> {
                decoderResults[DecoderID] = decoderResult;
                addRequests(Period, ChannelsNumber, (DecoderID + 1), decoderResults, mixResultListener);
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
        addRequests(Period, ChannelsNumber, 0, decoderResults, mixResultListener);
    }

    public void pause() {
        for (DecoderManagerWithStorage decoder : decoders) decoder.pause();
    }

    public void restart() {
        for (DecoderManagerWithStorage decoder : decoders) decoder.restart();
    }

    private int getGreaterNumberSamples() {
        int GreaterNumberSamples = decoders[0].getNumberOfSamples();
        for (DecoderManagerWithStorage decoderManager : decoders) {
            int numberOfSamples = decoderManager.getNumberOfSamples();
            if (numberOfSamples > GreaterNumberSamples) GreaterNumberSamples = numberOfSamples;
        }
        return GreaterNumberSamples;
    }

    private int getBiggerSampleDuration(DecoderManagerWithStorage[] decoders) {
        AtomicInteger BiggerSampleDuration = new AtomicInteger();
        CountDownLatch countDownLatch = new CountDownLatch(decoders.length);
        for (DecoderManagerWithStorage decoder : decoders) {
            decoder.addOnReadyListener(sampleMetrics -> {
                if (sampleMetrics.SampleDuration > BiggerSampleDuration.get())
                    BiggerSampleDuration.set(sampleMetrics.SampleDuration);
                countDownLatch.countDown();
            });
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return BiggerSampleDuration.get();
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

    public void start() {
        encoder = new EncoderCodecManager(NewMediaFormat);
        encoder.addOnOutputListener(encoderResult -> {
            Log.i("OnOutputListener", "encoderResult: " + encoderResult.bufferInfo.presentationTimeUs);
            ConverterListener.onConvert(encoderResult);
        });
        encoder.addOnFinishListener(FinishListener::OnFinish);

        for (DecoderManagerWithStorage decoderManager : decoders) decoderManager.restart();

        int lastPeriod = getGreaterNumberSamples() - 1;

        int ChannelsNumber = decoders[0].ChannelsNumber;

        encoder.setSampleDuration(getBiggerSampleDuration(decoders));

        for (int i = 0; i < lastPeriod; i++) {
            if (decoders.length > 1)
                mixTracks(i, ChannelsNumber, MixResult -> encoder.addPutInputRequest(MixResult));
            else {
                decoders[0].makeRequest(new PeriodRequest(i,
                        decoderResult -> encoder.addPutInputRequest(decoderResult.bytes)));
            }
        }

        if (decoders.length > 1) {
            mixTracks(lastPeriod, ChannelsNumber, MixResult -> {
                encoder.addPutInputRequest(MixResult);
                encoder.stop();
            });
        } else {
            decoders[0].makeRequest(new PeriodRequest(lastPeriod, decoderResult -> {
                encoder.addPutInputRequest(decoderResult.bytes);
                encoder.stop();
            }));
        }
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
