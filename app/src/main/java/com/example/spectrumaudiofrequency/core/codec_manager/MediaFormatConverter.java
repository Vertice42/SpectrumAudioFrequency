package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaFormat;
import android.util.Log;

import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.OnOutputListener;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.DecoderResult;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.PeriodRequest;

import java.util.concurrent.CountDownLatch;

import static com.example.spectrumaudiofrequency.sinusoid_converter.MixSample.Mix;

public class MediaFormatConverter {
    private final DecoderManagerWithStorage[] decoders;
    private final MediaFormat NewMediaFormat;
    boolean IsStarted = false;
    private EncoderCodecManager encoder;
    private MediaFormatConverterFinishListener FinishListener;
    private MediaFormatConverterListener ConverterListener;
    private long MediaDuration = 0;
    private int SampleSize;

    public MediaFormatConverter(Context context, int[] MediaIds, MediaFormat newMediaFormat) {
        decoders = new DecoderManagerWithStorage[MediaIds.length];
        for (int i = 0; i < MediaIds.length; i++)
            decoders[i] = new DecoderManagerWithStorage(context, MediaIds[i]);


        int sampleRate = newMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int bitRate = newMediaFormat.getInteger(MediaFormat.KEY_BIT_RATE);
        //Log.i("sampleRate", "" + sampleRate + " bitRate:" + bitRate);
        // newMediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, (int) (sampleRate / 2f));


        this.NewMediaFormat = newMediaFormat;
    }

    private void addRequests(int Period, int ChannelsNumber, int DecoderID,
                             DecoderResult[] decoderResults, MixResultListener mixResultListener) {
        if (DecoderID < decoders.length) {
            decoders[DecoderID].addRequest(new PeriodRequest(Period, decoderResult -> {
                Log.i("decoderSize", "" + decoders[DecoderID].NewSampleSize);
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
        for (DecoderManagerWithStorage decoder : decoders) decoder.start();
    }

    private int getGreaterNumberSamples() {
        int NumberOfSamples = decoders[0].getNumberOfSamples();
        for (DecoderManagerWithStorage decoderManager : decoders) {
            int numberOfSamples = decoderManager.getNumberOfSamples();
            if (numberOfSamples < NumberOfSamples)
                NumberOfSamples = numberOfSamples;
        }
        return NumberOfSamples;
    }

    public void start() {
        encoder = new EncoderCodecManager(NewMediaFormat);

        encoder.addOnOutputListener(encoderResult -> ConverterListener.onConvert(encoderResult));
        encoder.addFinishListener(FinishListener::OnFinish);
        int SampleDuration = 30000;
        encoder.setSampleDuration(SampleDuration);

        for (DecoderManagerWithStorage decoderManager : decoders) {
            decoderManager.setNewSampleDuration(SampleDuration);
            decoderManager.start();
        }

        int NumberOfSamples = getGreaterNumberSamples();
        int ChannelsNumber = decoders[0].ChannelsNumber;

        int lastPeriod = NumberOfSamples - 5;
        for (int i = 0; i < lastPeriod; i++) {
            if (decoders.length > 1)
                mixTracks(i, ChannelsNumber, MixResult -> encoder.addPutInputRequest(MixResult));
            else {
                decoders[0].addRequest(new PeriodRequest(i, decoderResult ->
                        encoder.addPutInputRequest(decoderResult.bytes)));
            }
        }

        if (decoders.length > 1) {
            mixTracks(lastPeriod, ChannelsNumber, MixResult -> {
                encoder.addPutInputRequest(MixResult);
                encoder.stop();
            });
        } else {
            decoders[0].addRequest(new PeriodRequest(lastPeriod, decoderResult -> {
                encoder.addPutInputRequest(decoderResult.bytes);
                encoder.stop();
            }));
        }
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
                long trueMediaDuration = decoderManager.getTrueMediaDuration();
                if (trueMediaDuration > MediaDuration) {
                    MediaDuration = trueMediaDuration;
                }
            }
        }
        return MediaDuration;
    }

    public MediaFormat getOutputFormat() {
        if (IsStarted) {
            OnOutputListener[] onFirstSample = new OnOutputListener[1];
            CountDownLatch awaitFirst = new CountDownLatch(1);
            onFirstSample[0] = codecSample -> {
                IsStarted = true;
                awaitFirst.countDown();
                encoder.removeOnOutputListener(onFirstSample[0]);
            };
            encoder.addOnOutputListener(onFirstSample[0]);
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
