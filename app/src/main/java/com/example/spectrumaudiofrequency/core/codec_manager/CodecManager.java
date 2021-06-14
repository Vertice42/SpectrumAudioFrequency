package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static android.media.MediaCodec.CONFIGURE_FLAG_ENCODE;
import static android.media.MediaFormat.KEY_BIT_RATE;
import static android.media.MediaFormat.KEY_CAPTURE_RATE;
import static android.media.MediaFormat.KEY_DURATION;
import static android.media.MediaFormat.KEY_FRAME_RATE;

public abstract class CodecManager {
    private final LinkedList<Integer> InputIdsAvailable = new LinkedList<>();
    private final LinkedList<IdListener> RequestsOfInputID = new LinkedList<>();

    private final LinkedList<OutputPromise> OutputPromises = new LinkedList<>();
    private final LinkedList<OutputPromise> SortedOutputPromises = new LinkedList<>();

    private final LinkedList<IdListener> OnInputIdAvailableListeners = new LinkedList<>();
    private final LinkedList<SampleMetricsListener> onReadyListeners = new LinkedList<>();
    private final LinkedList<OnOutputListener> OnOutputListeners = new LinkedList<>();
    private final LinkedList<Runnable> OnInputIdReleasedListeners = new LinkedList<>();
    private final LinkedList<Runnable> OnFinishListeners = new LinkedList<>();

    private final SortedQueue samplesQueue = new SortedQueue();

    public long MediaDuration;
    protected int SampleDuration;
    protected int SampleSize;
    protected boolean IsStopped = false;
    private int bufferLimit = 0;
    private int AmountOfBuffers;
    private MediaCodec Codec;
    private ResultPromiseListener onPromiseKept;
    private boolean IsReady;
    private int ResultsExpected = 0;

    public static MediaFormat copyMediaFormat(MediaFormat mediaFormat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new MediaFormat(mediaFormat);
        } else {
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);

            MediaFormat format = null;

            if (mime.contains("video")) {
                int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
                int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                format = MediaFormat.createVideoFormat(mime, width, height);

                format.setInteger(KEY_FRAME_RATE, mediaFormat.getInteger(KEY_FRAME_RATE));
                if (format.containsKey(KEY_CAPTURE_RATE))
                    format.setInteger(KEY_CAPTURE_RATE, mediaFormat.getInteger(KEY_CAPTURE_RATE));

            } else if (mime.contains("audio")) {
                int channelCount = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                int sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                format = MediaFormat.createAudioFormat(mime, sampleRate, channelCount);
            }

            assert format != null;
            format.setLong(KEY_DURATION, mediaFormat.getLong(KEY_DURATION));

            int bit_rate = -1;
            if (mediaFormat.containsKey(KEY_BIT_RATE)) {
                bit_rate = mediaFormat.getInteger(KEY_BIT_RATE);
            } else if (mediaFormat.containsKey("bit-rate")) {
                bit_rate = mediaFormat.getInteger("bit-rate");
            }
            format.setInteger(KEY_BIT_RATE, bit_rate);
            return format;
        }
    }

    protected void prepare(MediaFormat mediaFormat, boolean IsDecoder) {
        try {
            if (IsDecoder)
                Codec = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
            else
                Codec = MediaCodec.createEncoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaDuration = mediaFormat.getLong(KEY_DURATION);

        AtomicBoolean IsMaxBufferValue = new AtomicBoolean(false);
        AtomicLong lastSampleTime = new AtomicLong();

        AtomicReference<IdListener> getAmountOfBuffers = new AtomicReference<>();
        getAmountOfBuffers.set(InputID -> {
            if (InputID > AmountOfBuffers) {
                AmountOfBuffers = InputID;
            } else if (InputID < AmountOfBuffers) {
                AmountOfBuffers += 1;
                IsMaxBufferValue.set(true);
                removeOnInputIdAvailableListeners(getAmountOfBuffers.get());
            }
        });

        addOnInputIdAvailableListeners(getAmountOfBuffers.get());
        addOnInputIdAvailableListeners(this::giveBackInputID);

        onPromiseKept = codecSample -> {
            long sampleTime = codecSample.bufferInfo.presentationTimeUs;
            int sampleDuration = (int) (sampleTime - lastSampleTime.get());
            lastSampleTime.set(sampleTime);

            samplesQueue.add(codecSample);
            if (IsMaxBufferValue.get() &&
                    sampleDuration != 0 &&
                    codecSample.bytes.length > 0 &&
                    sampleTime == sampleDuration * 4) {
                executeOnReadyListeners(sampleDuration, codecSample.bytes.length);
                onPromiseKept = this::orderSamples;
            }
        };

        Codec.setCallback(new MediaCodec.Callback() {

            @Override
            public void onInputBufferAvailable(@NotNull final MediaCodec mediaCodec,
                                               final int inputBufferId) {
                executeOnInputIdAvailableListeners(inputBufferId);
                executeOnInputIdAvailableListeners();
            }

            @Override
            public void onOutputBufferAvailable(@NotNull final MediaCodec mediaCodec,
                                                final int outputBufferId,
                                                @NotNull final BufferInfo bufferInfo) {
                KeepOutputPromises(outputBufferId, bufferInfo);
            }

            @Override
            public void onError(@NotNull final MediaCodec mediaCodec,
                                @NotNull final MediaCodec.CodecException e) {
                Log.e("MediaCodecERROR", "onError: ", e);
            }

            @Override
            public void onOutputFormatChanged(@NotNull final MediaCodec mediaCodec,
                                              @NotNull final MediaFormat mediaFormat) {
                Log.i("CodecManager", "onOutputFormatChanged: "
                        + mediaFormat.toString());
            }
        });

        if (IsDecoder) {
            Codec.configure(mediaFormat, null, null, 0);
        } else {
            Codec.configure(mediaFormat, null, null, CONFIGURE_FLAG_ENCODE);
        }
        Codec.start();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.i("CodecInfo", Codec.getCodecInfo().getCanonicalName());
        }
    }

    public synchronized void giveBackInputID(int BufferId) {
        if (RequestsOfInputID.size() > 0) {
            IdListener idListener = RequestsOfInputID.get(0);
            RequestsOfInputID.remove(idListener);
            idListener.onIdAvailable(BufferId);
        } else InputIdsAvailable.add(BufferId);
    }

    private void deliverResult(boolean isLasPeace) {
        CodecSample codecSample = (CodecSample) samplesQueue.pollFirst();
        codecSample.isLastPeace = isLasPeace;
        keepPromisesOfSortedOutputs(codecSample);
        executeOnOutputListener(codecSample);
        ResultsExpected--;
    }

    private void orderSamples(CodecSample codecSample) {
        samplesQueue.add(codecSample);
        if (IsStopped && samplesQueue.size() == ResultsExpected) {
            while (samplesQueue.size() > 1) deliverResult(false);
            deliverResult(true);
            executeFinishListeners();
        } else if (samplesQueue.size() >= AmountOfBuffers)
            for (int i = 0; i < AmountOfBuffers; i++) deliverResult(false);
    }

    public int getInputBufferLimit() {
        if (bufferLimit == 0) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            this.addInputIdRequest(Id -> {
                bufferLimit = getInputBuffer(Id).limit();
                giveBackInputID(Id);
                countDownLatch.countDown();
            });
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.wtf("awaitInputBufferLimit", e);
            }
        }
        return bufferLimit;
    }

    public int getNumberOfInputsIdsAvailable() {
        /*
        Log.i("Ids", "InputsIdsAvailable = " + InputIdsAvailable.size() +
                " RequestsOfInputID = " + RequestsOfInputID.size() +
                " AmountOfBuffers:" + AmountOfBuffers);
         */
        return InputIdsAvailable.size();
    }

    protected MediaFormat getOutputFormat() {
        return Codec.getOutputFormat();
    }

    void putAndProcessInput(int inputBufferId,
                            byte[] data,
                            BufferInfo bufferInfo) {
        getInputBuffer(inputBufferId).put(data);
        processInput(inputBufferId, bufferInfo);
    }

    void processInput(int inputBufferId,
                      BufferInfo bufferInfo,
                      ResultPromiseListener promiseListener) {
        SortedOutputPromises.add(new OutputPromise(bufferInfo.presentationTimeUs, promiseListener));
        processInput(inputBufferId, bufferInfo);
    }

    void processInput(int inputBufferId, BufferInfo bufferInfo) {
        OutputPromises.add(new OutputPromise(bufferInfo.presentationTimeUs, onPromiseKept));
        ResultsExpected++;
        Codec.queueInputBuffer(inputBufferId,
                bufferInfo.offset,
                bufferInfo.size,
                bufferInfo.presentationTimeUs,
                bufferInfo.flags);
    }

    public void stop() {
        IsStopped = true;
    }

    protected synchronized void addInputIdRequest(IdListener idListener) {
        if (InputIdsAvailable.size() > 0) {
            int InputId = InputIdsAvailable.get(0);
            InputIdsAvailable.remove(0);
            idListener.onIdAvailable(InputId);
        } else {
            RequestsOfInputID.add(idListener);
        }
    }

    public void removeOnInputIdAvailableListener(Runnable OnInputIdAvailableListener) {
        OnInputIdReleasedListeners.add(OnInputIdAvailableListener);

    }

    public void addOnInputIdAvailableListener(Runnable OnInputIdAvailableListener) {
        OnInputIdReleasedListeners.add(OnInputIdAvailableListener);
    }

    private synchronized void executeOnInputIdAvailableListeners() {
        for (int i = 0; i < OnInputIdReleasedListeners.size(); i++) {
            OnInputIdReleasedListeners.get(i).run();
        }
    }

    private void addOnInputIdAvailableListeners(IdListener idListener) {
        OnInputIdAvailableListeners.add(idListener);
    }

    private void removeOnInputIdAvailableListeners(IdListener idListener) {
        OnInputIdAvailableListeners.remove(idListener);
    }

    private void executeOnInputIdAvailableListeners(int InputId) {
        for (int i = 0; i < OnInputIdAvailableListeners.size(); i++)
            OnInputIdAvailableListeners.get(i).onIdAvailable(InputId);
    }

    public void addOnReadyListener(SampleMetricsListener onReadyListener) {
        if (IsReady) onReadyListener.OnAvailable(new SampleMetrics(SampleDuration, SampleSize));
        else onReadyListeners.add(onReadyListener);
    }

    private void executeOnReadyListeners(int sampleDuration, int sampleLength) {
        IsReady = true;
        SampleDuration = sampleDuration;
        SampleSize = sampleLength;
        for (int i = 0; i < onReadyListeners.size(); i++)
            onReadyListeners.get(i).OnAvailable(new SampleMetrics(sampleDuration, sampleLength));
    }

    private void keepPromisesOfSortedOutputs(CodecSample codecSample) {
        int i = 0;
        while (i < SortedOutputPromises.size()) {
            OutputPromise promise = SortedOutputPromises.get(i);
            if (promise.SampleTime == codecSample.bufferInfo.presentationTimeUs) {
                promise.PromiseListener.onKeep(codecSample);
                SortedOutputPromises.remove(promise);
            } else i++;
        }
    }

    private synchronized void KeepOutputPromises(int outputBufferId, BufferInfo bufferInfo) {
        for (int i = 0; i < OutputPromises.size(); i++) {
            OutputPromise outputPromise = OutputPromises.get(i);
            if (outputPromise.SampleTime == bufferInfo.presentationTimeUs) {
                OutputPromises.remove(outputPromise);
                ByteBuffer outputBuffer = Codec.getOutputBuffer(outputBufferId);
                byte[] bytes = new byte[outputBuffer.remaining()];
                outputBuffer.get(bytes);
                Codec.releaseOutputBuffer(outputBufferId, false);
                outputPromise.PromiseListener.onKeep(new CodecSample(bufferInfo, bytes));
                break;
            }
        }
    }

    public void addOnFinishListener(Runnable finishListener) {
        OnFinishListeners.add(finishListener);
    }

    public void removeOnFinishListener(Runnable finishListener) {
        OnFinishListeners.remove(finishListener);
    }

    private void executeFinishListeners() {
        for (int i = 0; i < OnFinishListeners.size(); i++) {
            OnFinishListeners.get(i).run();
        }
    }

    public boolean IsReady() {
        return IsReady;
    }

    public void addOnOutputListener(OnOutputListener onOutputListener) {
        OnOutputListeners.add(onOutputListener);
    }

    public void removeOnOutputListener(OnOutputListener onOutputListener) {
        OnOutputListeners.add(onOutputListener);
    }

    private void executeOnOutputListener(CodecSample codecSample) {
        for (int i = 0; i < OnOutputListeners.size(); i++) {
            OnOutputListeners.get(i).OnOutput(codecSample);
        }
    }

    public ByteBuffer getInputBuffer(int inputID) {
        return Codec.getInputBuffer(inputID);
    }

    public interface ResultPromiseListener {
        void onKeep(CodecSample codecSample);
    }

    public interface IdListener {
        void onIdAvailable(int Id);
    }

    public interface OnOutputListener {
        void OnOutput(CodecSample codecSample);
    }

    public interface SampleMetricsListener {
        void OnAvailable(SampleMetrics sampleMetrics);
    }

    public static class OutputPromise {
        long SampleTime;
        ResultPromiseListener PromiseListener;

        public OutputPromise(long sampleTime, ResultPromiseListener PromiseListener) {
            SampleTime = sampleTime;
            this.PromiseListener = PromiseListener;
        }
    }

    public static class CodecSample implements QueueElement {
        public BufferInfo bufferInfo;
        public byte[] bytes;
        public boolean isLastPeace;

        public CodecSample(BufferInfo bufferInfo, byte[] bytes) {
            this.bufferInfo = bufferInfo;
            this.bytes = bytes;
            this.isLastPeace = false;
        }

        @Override
        public @NotNull String toString() {
            return "{" +
                    " T=" + bufferInfo.presentationTimeUs +
                    ", S=" + bytes.length +
                    '}';
        }

        @Override
        public long getIndex() {
            return this.bufferInfo.presentationTimeUs;
        }
    }

    public static class SampleMetrics {
        public int SampleDuration;
        public int SampleSize;

        public SampleMetrics(int sampleDuration, int sampleSize) {
            SampleDuration = sampleDuration;
            SampleSize = sampleSize;
        }
    }
}
