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

import static android.media.MediaExtractor.SEEK_TO_NEXT_SYNC;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class AudioDecoder {
    private ForkJoinPool Poll;
    private Activity activity;
    private Uri uri;
    private String AudioPath;
    private MediaCodec Decoder;

    public MediaFormat format;
    public MediaExtractor extractor;
    public int bufferDuration;

    private interface IdListener {
        void onIdAvailable(int Id);
    }

    public interface ProcessListener {
        void OnProceed(DecoderResult decoderResult);
    }

    private final ArrayList<IdListener> InputIDListeners = new ArrayList<>();
    private final ArrayList<Integer> InputIds = new ArrayList<>();

    public static class OutputPromise {
        public int id;
        public PeriodRequest periodRequest;

        OutputPromise(int id, PeriodRequest periodRequest) {
            this.id = id;
            this.periodRequest = periodRequest;
        }
    }

    public static class DecoderResult {
        short[][] SamplesChannels;
        long BufferDuration;

        DecoderResult(short[][] SamplesChannels, long BufferDuration) {
            this.SamplesChannels = SamplesChannels;
            this.BufferDuration = BufferDuration;
        }
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

    private final ArrayList<OutputPromise> ProcessPromises = new ArrayList<>();

    OutputPromise getOutputPromise(int id) {
        for (OutputPromise outputPromise : ProcessPromises) {
            if (outputPromise.id == id) {
                ProcessPromises.remove(outputPromise);
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

    private long Duration;

    /**
     * get Media Duration of File on MicroSeconds
     */
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
                    if (InputIDListeners.size() != 0) {
                        IdListener idListener = InputIDListeners.get(0);
                        InputIDListeners.remove(idListener);
                        idListener.onIdAvailable(0);
                    } else {
                        InputIds.add(inputBufferId);
                    }

                }

                @Override
                public void onOutputBufferAvailable(@NonNull final MediaCodec mediaCodec, final int outputBufferId,
                                                    @NonNull final MediaCodec.BufferInfo bufferInfo) {
                    DecoderResult decoderResult = processOutput(outputBufferId);
                    Poll.execute(() -> getOutputPromise(outputBufferId).periodRequest.ProcessListener.OnProceed(decoderResult));
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
            InputIDListeners.add(idListener);
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

    private void processRequest(int InputId, PeriodRequest periodRequest) {
        ByteBuffer buffer = Decoder.getInputBuffer(InputId);
        setTimeOfExtractor(periodRequest.RequiredPeriodTime);
        int sampleSize = extractor.readSampleData(buffer, 0);
        long presentationTimeUs = extractor.getSampleTime();

        ProcessPromises.add(new OutputPromise(InputId, periodRequest));
        if (sampleSize < 0) {
            Decoder.queueInputBuffer(InputId, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        } else {
            Decoder.queueInputBuffer(InputId, 0, sampleSize, presentationTimeUs, 0);
        }

    }

    public void addRequest(PeriodRequest periodRequest) {
        getInputId(InputID -> processRequest(InputID, periodRequest));
    }
}