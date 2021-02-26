package com.example.spectrumaudiofrequency;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;

import static android.media.MediaExtractor.SEEK_TO_NEXT_SYNC;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class AudioDecoder {
    private ForkJoinPool Poll;
    private Activity activity = null;
    private Uri uri = null;
    private String AudioPath;
    private MediaCodec Decoder;
    public MediaFormat format;
    public MediaExtractor extractor;
    public int bufferDuration;

    private interface IdListener {
        void onIdAvailable(int Id);
    }

    public interface ProcessListener {
        void OnProceed(short[][] SamplesChannels, long BufferDuration);
    }

    private final ArrayList<IdListener> idListeners = new ArrayList<>();
    private final ArrayList<Integer> InputIds = new ArrayList<>();

    static class OutputPromise {
        public int id;
        public IdListener idListener;

        OutputPromise(int id, IdListener idListener) {
            this.id = id;
            this.idListener = idListener;
        }
    }

    private final ArrayList<OutputPromise> OutputPromises = new ArrayList<>();

    OutputPromise getOutputPromise(int id) {
        for (OutputPromise outputPromise : OutputPromises) {
            if (outputPromise.id == id) {
                OutputPromises.remove(outputPromise);
                return outputPromise;
            }
        }
        return null;
    }

    AudioDecoder(Activity activity, Uri uri) {
        this.activity = activity;
        this.uri = uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.Poll = ForkJoinPool.commonPool();
        } else {
            this.Poll = new ForkJoinPool();
        }
        prepare();
    }

    AudioDecoder(String AudioPath) {
        this.AudioPath = AudioPath;
        prepare();
    }

    private void prepare() {
        this.Poll.execute(() -> {
            try {
                Decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_MPEG);
            } catch (IOException e) {
                e.printStackTrace();//todo add e
            }

            extractor = new MediaExtractor();
            try {
                if (AudioPath != null) extractor.setDataSource(AudioPath);
                else extractor.setDataSource(activity, uri, null);
            } catch (IOException e) {
                e.printStackTrace();//todo add error
            }

            format = extractor.getTrackFormat(0);
            extractor.selectTrack(0);

            Decoder.setCallback(new MediaCodec.Callback() {

                @Override
                public void onInputBufferAvailable(@NonNull final MediaCodec mediaCodec, final int inputBufferId) {
                    if (idListeners.size() != 0) {
                        IdListener idListener = idListeners.get(0);
                        idListeners.remove(idListener);
                        idListener.onIdAvailable(0);
                    } else {
                        InputIds.add(inputBufferId);
                    }

                }

                @Override
                public void onOutputBufferAvailable(@NonNull final MediaCodec mediaCodec, final int outputBufferId,
                                                    @NonNull final MediaCodec.BufferInfo bufferInfo) {
                    OutputPromise outputPromise = getOutputPromise(outputBufferId);
                    outputPromise.idListener.onIdAvailable(outputBufferId);
                }

                @Override
                public void onError(@NonNull final MediaCodec mediaCodec, @NonNull final MediaCodec.CodecException e) {
                    Log.e("MediaCodecERROR", "onError: ", e);
                }

                @Override
                public void onOutputFormatChanged(@NonNull final MediaCodec mediaCodec,
                                                  @NonNull final MediaFormat mediaFormat) {
                }
            });
            Decoder.configure(format, null, null, 0);
            Decoder.start();
        });
    }

    private void getInputId(IdListener idListener) {
        if (InputIds.size() > 0) {
            int InputId = InputIds.get(0);
            InputIds.remove(0);

            idListener.onIdAvailable(InputId);
        } else {
            idListeners.add(idListener);
        }
    }

    private void putDataOnInput(IdListener idListener) {
        getInputId(InputId -> {
            ByteBuffer buffer = Decoder.getInputBuffer(InputId);
            int sampleSize = extractor.readSampleData(buffer, 0);
            long presentationTimeUs = extractor.getSampleTime();

            OutputPromises.add(new OutputPromise(InputId, idListener));

            if (sampleSize < 0) {
                Decoder.queueInputBuffer(InputId, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else {
                Decoder.queueInputBuffer(InputId, 0, sampleSize, presentationTimeUs, 0);
            }
        });
    }

    static class DecoderResult {
        short[][] SamplesChannels;
        long BufferDuration;

        DecoderResult(short[][] SamplesChannels, long BufferDuration) {
            this.SamplesChannels = SamplesChannels;
            this.BufferDuration = BufferDuration;
        }
    }

    private DecoderResult processOutput(int OutputId) {

        ByteBuffer outputBuffer = Decoder.getOutputBuffer(OutputId);
        int channelsNumber = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

        ShortBuffer buffer = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer();

        short[][] SamplesChannels = new short[channelsNumber][buffer.remaining() / channelsNumber];

        for (int i = 0; i < SamplesChannels.length; ++i) {
            for (int j = 0; j < SamplesChannels[i].length; j++) {
                SamplesChannels[i][j] = buffer.get(j * channelsNumber + i);
            }
        }
        //separate channels

        Decoder.releaseOutputBuffer(OutputId, false);

        bufferDuration = format.getInteger(MediaFormat.KEY_SAMPLE_RATE) / channelsNumber;

        return new DecoderResult(SamplesChannels, bufferDuration);
    }

    /**
     * get Media Duration of File on MicroSeconds
     */
    private long Duration;

    long getDuration() {
        if (format != null) Duration = format.getLong(MediaFormat.KEY_DURATION);
        return Duration;
    }

    private long timeOfExtractor = 0;

    private void setTimeOfExtractor(long NewTime) {
        if (NewTime - timeOfExtractor > bufferDuration)
            extractor.seekTo(NewTime, SEEK_TO_NEXT_SYNC);
        else extractor.advance();

        timeOfExtractor = NewTime;
    }

    public static class PeriodRequest {
        long Time;
        long RequiredPeriodTime;

        ProcessListener ProcessListener;

        PeriodRequest(long Time, long RequiredPeriodTime, ProcessListener ProcessListener) {
            this.Time = Time;
            this.RequiredPeriodTime = RequiredPeriodTime;
            this.ProcessListener = ProcessListener;
        }
    }

    private final ArrayList<PeriodRequest> PeriodRequests = new ArrayList<>();

    public void addRequest(PeriodRequest periodRequest) {
        PeriodRequests.add(periodRequest);
        if (PeriodRequests.size() == 1) NextPeriodRequest();
    }

    private void NextPeriodRequest() {
        if (PeriodRequests.size() == 0) return;

        PeriodRequest periodRequest = PeriodRequests.get(0);
        setTimeOfExtractor(periodRequest.Time);

        getSample(periodRequest, 0, new short[0][0]);

        PeriodRequests.remove(periodRequest);
    }

    private void getSample(PeriodRequest periodRequest, long ObtainedPeriod, final short[][] SamplesChannels) {
        AtomicLong obtainedPeriod = new AtomicLong(ObtainedPeriod);

        putDataOnInput(ID -> {
            DecoderResult result = processOutput(ID);
            obtainedPeriod.addAndGet(result.BufferDuration);

            if (obtainedPeriod.get() >= periodRequest.RequiredPeriodTime) {
                Poll.submit(() -> periodRequest.ProcessListener.OnProceed
                        (result.SamplesChannels, obtainedPeriod.get()));
            } else {
                getSample(periodRequest, obtainedPeriod.get(),
                        Util.ConcatenateArray(SamplesChannels, result.SamplesChannels));
            }
        });
    }
}