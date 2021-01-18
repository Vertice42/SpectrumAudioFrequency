package com.example.spectrumaudiofrequency;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class AudioDecoder {
    private MediaCodec Decoder;
    public MediaFormat format;
    public MediaExtractor extractor;

    private interface InputIdListener {
        void onInputIdAvailable(int InputId);
    }

    private interface OutputIdAvailableListener {
        void onOutputIdAvailable(int OutputId);
    }

    public interface ProcessListener {
        void OnProceed(short[][] WavePiece, long BufferDuration);
    }

    private final ArrayList<InputIdListener> InputIdListeners = new ArrayList<>();
    private final ArrayList<Integer> InputIds = new ArrayList<>();

    static class OutputPromise {
        private final int InputId;
        private final long presentationTimeUs;
        public OutputIdAvailableListener outputIdAvailableListener;

        OutputPromise(int InputId, long presentationTimeUs, OutputIdAvailableListener outputIdAvailableListener) {
            this.InputId = InputId;
            this.presentationTimeUs = presentationTimeUs;
            this.outputIdAvailableListener = outputIdAvailableListener;
        }
    }

    private final ArrayList<OutputPromise> OutputPromises = new ArrayList<>();

    OutputPromise getOutputPromise(long presentationTimeUs) {
        for (OutputPromise outputPromise : OutputPromises) {
            if (outputPromise.presentationTimeUs == presentationTimeUs) {
                OutputPromises.remove(outputPromise);
                return outputPromise;
            }
        }
        //throw new Exception();
        return null;
    }

    AudioDecoder(String AudioPath) {
        new Thread(() -> {
            try {
                Decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_MPEG);
            } catch (IOException e) {
                e.printStackTrace();//todo add e
            }

            extractor = new MediaExtractor();
            try {
                extractor.setDataSource(AudioPath);
            } catch (IOException e) {
                e.printStackTrace();//todo add error
            }

            format = extractor.getTrackFormat(0);
            extractor.selectTrack(0);

            Decoder.setCallback(new MediaCodec.Callback() {

                @Override
                public void onInputBufferAvailable(@NonNull final MediaCodec mediaCodec, final int inputBufferId) {
                    if (InputIdListeners.size() != 0) {
                        InputIdListener inputIdListener = InputIdListeners.get(0);
                        InputIdListeners.remove(inputIdListener);
                        inputIdListener.onInputIdAvailable(0);
                    } else {
                        InputIds.add(inputBufferId);
                    }
                }

                @Override
                public void onOutputBufferAvailable(@NonNull final MediaCodec mediaCodec, final int outputBufferId,
                                                    @NonNull final MediaCodec.BufferInfo bufferInfo) {
                    OutputPromise outputPromise = getOutputPromise(bufferInfo.presentationTimeUs);
                    outputPromise.outputIdAvailableListener.onOutputIdAvailable(outputBufferId);
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
        }).start();
    }

    private void getInputId(InputIdListener inputIdListener) {
        if (InputIds.size() > 0) {
            int InputId = InputIds.get(0);
            InputIds.remove(0);

            inputIdListener.onInputIdAvailable(InputId);
        } else {
            InputIdListeners.add(inputIdListener);
        }
    }

    long[] ReposeTime = new long[20];

    private void startProcessing(OutputIdAvailableListener outputIdAvailableListener) {
        getInputId(InputId -> {
            ByteBuffer buffer = Decoder.getInputBuffer(InputId);
            int sampleSize = extractor.readSampleData(buffer, 0);
            long presentationTimeUs = extractor.getSampleTime();

            ReposeTime[InputId] = new Date().getTime();
            OutputPromises.add(new OutputPromise(InputId, presentationTimeUs, outputIdAvailableListener));

            if (sampleSize < 0) {
                Decoder.queueInputBuffer(InputId, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else {
                Decoder.queueInputBuffer(InputId, 0, sampleSize, presentationTimeUs, 0);
            }
        });
    }

    private void processOutput(int OutputId, ProcessListener processListener) {

        ByteBuffer outputBuffer = Decoder.getOutputBuffer(OutputId);
        int numChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

        ShortBuffer shortBuffer =
                outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer();

        short[][] WavePiece = new short[numChannels][shortBuffer.remaining() / numChannels];

        for (int i = 0; i < WavePiece.length; ++i) {
            for (int j = 0; j < WavePiece[i].length; j++) {
                WavePiece[i][j] = shortBuffer.get(j * numChannels + i);
            }
        }

        Decoder.releaseOutputBuffer(OutputId, false);

        int bufferDuration = format.getInteger(MediaFormat.KEY_SAMPLE_RATE) / numChannels;

        processListener.OnProceed(WavePiece, bufferDuration);
    }

    void Process(ProcessListener processListener) {
        startProcessing(OutputId -> processOutput(OutputId, processListener));
    }

    /**
     * get Media Duration of File on MicroSeconds
     */
    private long Duration;

    long getDuration() {
        if (format != null) Duration = format.getLong(MediaFormat.KEY_DURATION);
        return Duration;
    }

    private void setTime(long NewTime) {
        extractor.seekTo(NewTime, MediaExtractor.SEEK_TO_NEXT_SYNC);
    }

    public static class PeriodRequest {
        long Time;
        long RequiredPeriod;

        ProcessListener ProcessListener;

        PeriodRequest(long Time, long RequiredPeriod, ProcessListener ProcessListener) {
            this.Time = Time;
            this.RequiredPeriod = RequiredPeriod;
            this.ProcessListener = ProcessListener;
        }
    }

    ArrayList<PeriodRequest> PeriodRequests = new ArrayList<>();

    public void addRequest(PeriodRequest periodRequest) {
        PeriodRequests.add(periodRequest);
        if (PeriodRequests.size() == 1) NextPeriodRequest();
    }

    private void NextPeriodRequest() {
        if (PeriodRequests.size() == 0) return;

        PeriodRequest periodRequest = PeriodRequests.get(0);
        setTime(periodRequest.Time);

        getPeriod(periodRequest.RequiredPeriod, (ShortArrays, PresentationTimeUs) -> {
            PeriodRequests.get(0).ProcessListener.OnProceed(ShortArrays, PresentationTimeUs);
            PeriodRequests.remove(0);
            NextPeriodRequest();

        }, 0, new short[0][0]);
    }

    private void getPeriod(long Period, ProcessListener processListener, long ObtainedPeriod, final short[][] WavePeaces) {
        AtomicLong obtainedPeriod = new AtomicLong(ObtainedPeriod);
        Process((WavePiece, WavePeaceDuration) -> {
            short[][] WavePieceConcatenated = Util.ConcatenateArray(WavePeaces, WavePiece);

            obtainedPeriod.addAndGet(WavePeaceDuration);
            if (obtainedPeriod.get() >= Period) {
                processListener.OnProceed(WavePieceConcatenated, obtainedPeriod.get());
            } else {
                getPeriod(Period, processListener, obtainedPeriod.get(), WavePieceConcatenated);
            }
        });
    }
}
