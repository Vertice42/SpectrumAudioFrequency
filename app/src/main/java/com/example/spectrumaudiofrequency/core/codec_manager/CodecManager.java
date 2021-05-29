package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.CONFIGURE_FLAG_ENCODE;
import static android.media.MediaFormat.KEY_BIT_RATE;
import static android.media.MediaFormat.KEY_CAPTURE_RATE;
import static android.media.MediaFormat.KEY_DURATION;
import static android.media.MediaFormat.KEY_FRAME_RATE;

public abstract class CodecManager {
    private final ArrayList<Integer> InputIdsAvailable = new ArrayList<>();
    private final ArrayList<OnReadyListener> onOnReadyListeners = new ArrayList<>();
    private final ArrayList<IdListener> RequestsOfInputID = new ArrayList<>();
    private final ArrayList<OutputPromise> OutputPromises = new ArrayList<>();
    private final ArrayList<Runnable> FinishListeners = new ArrayList<>();
    private final ArrayList<OutputPromise> PromisesOfSortedOutputs = new ArrayList<>();
    private final ArrayList<OnOutputListener> OnOutputListeners = new ArrayList<>();
    private final LinkedList<IdListener> InputIdListeners = new LinkedList<>();

    private final SortedQueue samplesQueue = new SortedQueue();
    public MediaFormat mediaFormat;
    public long MediaDuration;
    protected ExecutorService CachedThreadPool;
    protected int NewSampleDuration;
    protected int NewSampleSize;
    boolean IsStopped = false;
    private MediaCodec Codec = null;
    private int bufferLimit = 0;
    private int AmountOfBuffers;
    private ResultPromiseListener onPromiseKept;
    private boolean IsReady;

    public CodecManager(MediaFormat mediaFormat, boolean IsDecoder) {
        PrepareEndStart(mediaFormat, IsDecoder);
    }

    CodecManager() {
    }

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
            format.setInteger(KEY_BIT_RATE, mediaFormat.getInteger(KEY_BIT_RATE));

            return format;
        }
    }

    protected void PrepareEndStart(MediaFormat mediaFormat, boolean IsDecoder) {
        this.mediaFormat = mediaFormat;
        this.CachedThreadPool = Executors.newCachedThreadPool();

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

        final IdListener[] awaitForAmountOfBuffers = new IdListener[1];
        awaitForAmountOfBuffers[0] = InputID -> {
            if (InputID > AmountOfBuffers) {
                AmountOfBuffers = InputID;
            } else if (InputID < AmountOfBuffers) {
                AmountOfBuffers += 1;
                IsMaxBufferValue.set(true);
                removeInputIdAvailableListener(awaitForAmountOfBuffers[0]);
            }
        };

        addInputIdAvailableListener(awaitForAmountOfBuffers[0]);
        addInputIdAvailableListener(this::GiveBackInputID);

        onPromiseKept = codecSample -> {
            long sampleTime = codecSample.bufferInfo.presentationTimeUs;
            int sampleDuration = (int) (sampleTime - lastSampleTime.get());
            lastSampleTime.set(sampleTime);

            samplesQueue.add(codecSample);
            if (IsMaxBufferValue.get())
                if (sampleDuration != 0 &&
                        codecSample.bytes.length > 0 &&
                        sampleTime > sampleDuration * 4) {
                    ExecuteDecoderReadyListeners(sampleDuration, codecSample.bytes.length);
                    onPromiseKept = this::OrderSamples;
                }
        };

        Codec.setCallback(new MediaCodec.Callback() {

            @Override
            public void onInputBufferAvailable(@NonNull final MediaCodec mediaCodec,
                                               final int inputBufferId) {
                executeInputIdListeners(inputBufferId);
            }

            @Override
            public void onOutputBufferAvailable(@NonNull final MediaCodec mediaCodec,
                                                final int outputBufferId,
                                                @NonNull final BufferInfo bufferInfo) {
                KeepOutputPromises(outputBufferId, bufferInfo);
            }

            @Override
            public void onError(@NonNull final MediaCodec mediaCodec,
                                @NonNull final MediaCodec.CodecException e) {
                Log.e("MediaCodecERROR", "onError: ", e);
            }

            @Override
            public void onOutputFormatChanged(@NonNull final MediaCodec mediaCodec,
                                              @NonNull final MediaFormat mediaFormat) {
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

    public synchronized void GiveBackInputID(int BufferId) {
        if (RequestsOfInputID.size() > 0) {
            IdListener idListener = RequestsOfInputID.get(0);
            RequestsOfInputID.remove(idListener);
            idListener.onIdAvailable(BufferId);
        } else InputIdsAvailable.add(BufferId);
    }

    private void OrderSamples(CodecSample codecSample) {
        samplesQueue.add(codecSample);
        if (IsStopped && PromisesOfSortedOutputs.size() == samplesQueue.size()) {
            while (samplesQueue.size() > 1) {
                CodecSample sample = (CodecSample) samplesQueue.pollFirst();
                assert sample != null;
                keepPromisesOfSortedOutputs(sample);
            }
            CodecSample sample = (CodecSample) samplesQueue.pollFirst();
            assert sample != null;
            sample.bufferInfo.flags = BUFFER_FLAG_END_OF_STREAM;
            keepPromisesOfSortedOutputs(sample);
            executeFinishListeners();

        } else if (samplesQueue.size() >= AmountOfBuffers) {
            for (int i = 0; i < AmountOfBuffers; i++) {
                CodecSample sample = (CodecSample) samplesQueue.pollFirst();
                assert sample != null;
                keepPromisesOfSortedOutputs(sample);
            }
        }
    }

    private void ExecuteDecoderReadyListeners(int sampleDuration, int sampleLength) {
        IsReady = true;
        NewSampleDuration = sampleDuration;
        NewSampleSize = sampleLength;
        for (int i = 0; i < onOnReadyListeners.size(); i++)
            onOnReadyListeners.get(i).OnReady(sampleDuration, sampleLength);
    }

    private synchronized void KeepOutputPromises(int outputBufferId, BufferInfo bufferInfo) {
        int i = 0;
        while (i < OutputPromises.size()) {
            OutputPromise outputPromise = OutputPromises.get(i);
            if (outputPromise.SampleTime == bufferInfo.presentationTimeUs) {
                OutputPromises.remove(outputPromise);
                ByteBuffer outputBuffer = Codec.getOutputBuffer(outputBufferId);
                byte[] bytes = new byte[outputBuffer.remaining()];
                outputBuffer.get(bytes);
                Codec.releaseOutputBuffer(outputBufferId, false);
                outputPromise.resultPromiseListener.onKeep(new CodecSample(bufferInfo, bytes));
            } else i++;
        }
    }

    private void keepPromisesOfSortedOutputs(CodecSample codecSample) {
        int i = 0;
        while (i < PromisesOfSortedOutputs.size()) {
            OutputPromise promise = PromisesOfSortedOutputs.get(i);
            if (promise.SampleTime == codecSample.bufferInfo.presentationTimeUs) {
                promise.resultPromiseListener.onKeep(codecSample);
                PromisesOfSortedOutputs.remove(promise);
            } else i++;
        }
        executeOnOutputListener(codecSample);
    }

    public int getFinishListenerSize() {
        return FinishListeners.size();
    }

    public int getReadyListenersSize() {
        return onOnReadyListeners.size();
    }

    public int getInputBufferLimit() {
        if (bufferLimit == 0) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            this.addInputIdRequest(Id -> {
                bufferLimit = getInputBuffer(Id).limit();
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
        //Log.i("Ids", "InputsIdsAvailable = " + InputIdsAvailable.size() + " RequestsOfInputID = " + RequestsOfInputID.size());
        return InputIdsAvailable.size();
    }

    protected MediaFormat getOutputFormat() {
        return Codec.getOutputFormat();
    }

    void addInputIdRequest(IdListener idListener) {
        if (InputIdsAvailable.size() > 0) {
            int InputId = InputIdsAvailable.get(0);
            InputIdsAvailable.remove(0);
            idListener.onIdAvailable(InputId);
        } else {
            RequestsOfInputID.add(idListener);
        }
    }

    void processInput(CodecManagerRequest codecManagerRequest) {
        Codec.queueInputBuffer(codecManagerRequest.BufferId,
                codecManagerRequest.bufferInfo.offset,
                codecManagerRequest.bufferInfo.size,
                codecManagerRequest.bufferInfo.presentationTimeUs,
                codecManagerRequest.bufferInfo.flags);
    }

    protected void addInputIdAvailableListener(IdListener idListener) {
        InputIdListeners.add(idListener);
    }

    protected void removeInputIdAvailableListener(IdListener idListener) {
        InputIdListeners.remove(idListener);
    }

    private void executeInputIdListeners(int InputId) {
        for (int i = 0; i < InputIdListeners.size(); i++)
            InputIdListeners.get(i).onIdAvailable(InputId);
    }

    public void addOnReadyListener(OnReadyListener onReadyListener) {
        if (IsReady) onOnReadyListeners.add(onReadyListener);
        else onReadyListener.OnReady(this.NewSampleDuration, this.NewSampleSize);
    }

    public void removeOnReadyListener(OnReadyListener onReadyListener) {
        onOnReadyListeners.remove(onReadyListener);
    }

    protected void addOrderlyOutputPromise(OutputPromise outputPromise) {
        OutputPromises.add(new OutputPromise(outputPromise.SampleTime, onPromiseKept));
        PromisesOfSortedOutputs.add(outputPromise);
    }

    public void addFinishListener(Runnable finishListener) {
        FinishListeners.add(finishListener);
    }

    public void removeOnFinishListener(Runnable finishListener) {
        FinishListeners.remove(finishListener);
    }

    private void executeFinishListeners() {
        for (int i = 0; i < FinishListeners.size(); i++) {
            CachedThreadPool.execute(FinishListeners.get(i));
        }
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

    public void stop() {
        IsStopped = true;
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

    public interface OnReadyListener {
        void OnReady(int SampleDuration, int SampleSize);
    }

    public static class CodecManagerRequest {
        public final int BufferId;
        public final BufferInfo bufferInfo;

        public CodecManagerRequest(int bufferId, BufferInfo bufferInfo) {
            BufferId = bufferId;
            this.bufferInfo = bufferInfo;
        }

        @Override
        public @NotNull String toString() {
            return "CodecManagerRequest{" +
                    "BufferId=" + BufferId +
                    ", bufferInfo {" +
                    "size=" + bufferInfo.size +
                    ", presentationTimeUs=" + bufferInfo.presentationTimeUs +
                    ", flags=" + bufferInfo.flags + '}' +
                    '}';
        }
    }

    public static class OutputPromise {
        long SampleTime;
        ResultPromiseListener resultPromiseListener;

        public OutputPromise(long sampleTime, ResultPromiseListener resultPromiseListener) {
            SampleTime = sampleTime;
            this.resultPromiseListener = resultPromiseListener;
        }
    }

    public static class CodecSample implements QueueElement {
        public BufferInfo bufferInfo;
        public byte[] bytes;

        public CodecSample(BufferInfo bufferInfo, byte[] bytes) {
            this.bufferInfo = bufferInfo;
            this.bytes = bytes;
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
}
