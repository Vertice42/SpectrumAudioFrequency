package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaFormat;
import android.util.Log;

import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.ResultPromiseListener;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.DecoderResult;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.PeriodRequest;
import com.example.spectrumaudiofrequency.util.CalculatePerformance;

import java.util.concurrent.CountDownLatch;

import static com.example.spectrumaudiofrequency.sinusoid_converter.MixSample.Mix;

public class MediaFormatConverter {
    private final DecoderManagerWithStorage[] decoderManagers;
    private final MediaFormat NewMediaFormat;
    boolean IsStarted = false;
    CountDownLatch awaitFirst = new CountDownLatch(1);
    private EncoderCodecManager encoder;
    private MediaFormatConverterFinishListener FinishListener;
    private MediaFormatConverterListener ConverterListener;
    private long MediaDuration = 0;
    private int SampleSize;

    public MediaFormatConverter(Context context, int[] MediaIds, MediaFormat newMediaFormat) {
        decoderManagers = new DecoderManagerWithStorage[MediaIds.length];
        for (int i = 0; i < MediaIds.length; i++)
            decoderManagers[i] = new DecoderManagerWithStorage(context, MediaIds[i]);

        /*
        int sampleRate = newMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int bitRate = newMediaFormat.getInteger(MediaFormat.KEY_BIT_RATE);
        //Log.i("sampleRate", "" + sampleRate + " bitRate:" + bitRate);
        //newMediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, (int) (sampleRate / 2f));

         */
        this.NewMediaFormat = newMediaFormat;
    }

    private void addRequests(int Period, int ChannelsNumber, int DecoderID, DecoderResult[] decoderResults, MixResultListener mixResultListener) {
        if (DecoderID < decoderManagers.length) {
            decoderManagers[DecoderID].addRequest(new PeriodRequest(Period, decoderResult -> {
                Log.i("decoderSize", "" + decoderManagers[DecoderID].NewSampleSize);
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
        DecoderResult[] decoderResults = new DecoderResult[decoderManagers.length];
        addRequests(Period, ChannelsNumber, 0, decoderResults, mixResultListener);
    }

    private int getGreaterNumberSamples() {
        int NumberOfSamples = decoderManagers[0].getNumberOfSamples();
        for (DecoderManagerWithStorage decoderManager : decoderManagers) {
            int numberOfSamples = decoderManager.getNumberOfSamples();
            if (numberOfSamples < NumberOfSamples)
                NumberOfSamples = numberOfSamples;
        }
        return NumberOfSamples;
    }

    private synchronized void executeOnConverter(CodecManager.CodecSample codecSample) {
        ConverterListener.onConvert(codecSample);
    }

    public void start() {
        encoder = new EncoderCodecManager(NewMediaFormat);

        ResultPromiseListener onFirst = null;
        final ResultPromiseListener FINAL_onFirst = onFirst;
        onFirst = codecSample -> {
            IsStarted = true;
            encoder.removeEncoderOutputListener(FINAL_onFirst);
            awaitFirst.countDown();
        };
        encoder.addEncoderOutputListener(onFirst);

        encoder.addEncoderOutputListener(encoderResult -> {
            CalculatePerformance.LogPercentage("Encoder ",
                    encoderResult.bufferInfo.presentationTimeUs,
                    encoder.MediaDuration);
            executeOnConverter(encoderResult);
        });
        encoder.addFinishListener(FinishListener::OnFinish);
        int SampleDuration = 30000;
        encoder.setSampleDuration(SampleDuration);

        for (DecoderManagerWithStorage decoderManager : decoderManagers) {
            decoderManager.setNewSampleDuration(SampleDuration);
            decoderManager.startDecoding();
        }

        int NumberOfSamples = getGreaterNumberSamples();
        int ChannelsNumber = decoderManagers[0].ChannelsNumber;

        int lastPeriod = NumberOfSamples - 5;
        for (int i = 0; i < lastPeriod; i++) {
            if (decoderManagers.length > 1)
                mixTracks(i, ChannelsNumber, MixResult -> encoder.addPutInputRequest(MixResult));
            else {
                decoderManagers[0].addRequest(new PeriodRequest(i, decoderResult -> {
                    encoder.addPutInputRequest(decoderResult.bytes);
                }));
            }
        }

        if (decoderManagers.length > 1) {
            mixTracks(lastPeriod, ChannelsNumber, MixResult -> {
                encoder.addPutInputRequest(MixResult);
                encoder.stop();
            });
        } else {
            decoderManagers[0].addRequest(new PeriodRequest(lastPeriod, decoderResult -> {
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
            for (DecoderManagerWithStorage decoderManager : decoderManagers) {
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
