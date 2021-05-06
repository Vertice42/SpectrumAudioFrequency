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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.CONFIGURE_FLAG_ENCODE;
import static android.media.MediaFormat.KEY_BIT_RATE;
import static android.media.MediaFormat.KEY_CAPTURE_RATE;
import static android.media.MediaFormat.KEY_DURATION;
import static android.media.MediaFormat.KEY_FRAME_RATE;

interface CodecManagerInterface {
    void NextPeace();
}

public abstract class CodecManager implements CodecManagerInterface {
    public interface OutputListener {
        void OnProceed(CodecManagerResult codecResult);
    }

    private final ArrayList<ReadyListener> ReadyListeners = new ArrayList<>();
    private final ArrayList<Integer> InputIdsAvailable = new ArrayList<>();
    private final ArrayList<IdListener> InputIDListeners = new ArrayList<>();
    private final ArrayList<OutputPromise> OutputPromises = new ArrayList<>();
    private final ArrayList<CodecFinishListener> EncoderFinishListeners = new ArrayList<>();

    public static class CodecManagerResult {
        public ByteBuffer OutputBuffer;
        public BufferInfo bufferInfo;

        public CodecManagerResult(ByteBuffer outputBuffer, BufferInfo bufferInfo) {
            OutputBuffer = outputBuffer;
            this.bufferInfo = bufferInfo;
        }

        @Override
        public @NotNull String toString() {
            return "CodecManagerResult{" +
                    "OutputBuffer=" + OutputBuffer.toString() +
                    ", bufferInfo = {" +
                    " size: " + bufferInfo.size +
                    " offset: " + bufferInfo.offset +
                    " presentationTimeUs: " + bufferInfo.presentationTimeUs +
                    " flags: " + bufferInfo.flags +
                    +'}' +
                    '}';
        }
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

    private final ArrayList<OutputOrderPromise> OrderedOutputPromises = new ArrayList<>();
    private final SampleQueue QueueOfSamples = new SampleQueue();
    protected ForkJoinPool forkJoinPool;
    boolean IsStopped = false;
    private MediaCodec Codec = null;
    private int bufferLimit = 0;

    public MediaFormat mediaFormat;
    public long MediaDuration;
    public int EncoderPadding = 0;
    private long lastSampleTime;
    private int AmountOfBuffers;
    private OutputListener onPromiseKept;
    private InputIDAvailableListener onInputBufferAvailable;

    public synchronized void GiveBackInputID(int BufferId) {
        if (InputIDListeners.size() > 0) {
            IdListener idListener = InputIDListeners.get(0);
            InputIDListeners.remove(idListener);
            idListener.onIdAvailable(BufferId);
        } else InputIdsAvailable.add(BufferId);
    }

    private synchronized void OrderSamples(CodecManagerResult codecResult) {
        boolean IsLastSample = codecResult == null;
        Log.i("AmountOfBuffers", "" + AmountOfBuffers + " InputIdsAvailable:" + InputIdsAvailable.size());
        if (!IsLastSample) {
            byte[] sample = new byte[codecResult.OutputBuffer.remaining()];
            codecResult.OutputBuffer.get(sample);
            QueueOfSamples.add(new CodecSample(codecResult.bufferInfo, sample));
        }
        if (IsStopped && InputIDListeners.size() == 0) {
            while (QueueOfSamples.size() > 1) KeepOrderedPromises(QueueOfSamples.peek());
            CodecSample sample = QueueOfSamples.peek();
            if (OutputPromises.size() == 0) {
                sample.bufferInfo.flags = BUFFER_FLAG_END_OF_STREAM;
                KeepOrderedPromises(sample);
                executeFinishListeners();
            } else KeepOrderedPromises(sample);
        } else if (QueueOfSamples.size() > AmountOfBuffers) {
            for (int i = 0; i < AmountOfBuffers; i++) KeepOrderedPromises(QueueOfSamples.peek());
        }

        if (!IsLastSample) NextPeace();
    }

    private synchronized void ExecuteDecoderReadyListeners(int sampleDuration, int sampleLength) {
        for (int i = 0; i < ReadyListeners.size(); i++)
            ReadyListeners.get(i).OnReady(sampleDuration, sampleLength);
    }

    private synchronized void KeepOutputPromises(int outputBufferId, BufferInfo bufferInfo) {
        int i = 0;
        while (i < OutputPromises.size()) {
            OutputPromise outputPromise = OutputPromises.get(i);
            if (outputPromise.SampleTime == bufferInfo.presentationTimeUs) {
                OutputPromises.remove(outputPromise);
                outputPromise.promiseResultListener.OnProceed(new CodecManagerResult(Codec.getOutputBuffer(outputBufferId), bufferInfo));
            } else i++;
        }
    }

    private synchronized void KeepOrderedPromises(CodecSample codecSample) {
        int i = 0;
        while (i < OrderedOutputPromises.size()) {
            OutputOrderPromise promise = OrderedOutputPromises.get(i);
            if (promise.SampleTime == codecSample.bufferInfo.presentationTimeUs) {
                promise.promiseResultListener.onKeep(codecSample);
                OrderedOutputPromises.remove(promise);
            } else i++;
        }
    }

    void PrepareEndStart(MediaFormat mediaFormat, boolean IsDecoder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            this.forkJoinPool = ForkJoinPool.commonPool();
        else this.forkJoinPool = new ForkJoinPool();
        this.mediaFormat = mediaFormat;
        try {
            if (IsDecoder)
                Codec = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
            else
                Codec = MediaCodec.createEncoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            e.printStackTrace();
        }
        String ENCODER_PADDING = "encoder-padding";
        if (mediaFormat.containsKey(ENCODER_PADDING))
            EncoderPadding = mediaFormat.getInteger(ENCODER_PADDING);
        MediaDuration = mediaFormat.getLong(KEY_DURATION);

        AtomicBoolean IsMaxBufferValue = new AtomicBoolean(false);

        onInputBufferAvailable = InputID -> {
            GiveBackInputID(InputID);
            if (InputID > AmountOfBuffers) {
                AmountOfBuffers = InputID;
            } else if (InputID < AmountOfBuffers) {
                AmountOfBuffers += 1;
                IsMaxBufferValue.set(true);
                onInputBufferAvailable = this::GiveBackInputID;
            }
        };

        onPromiseKept = codecResult -> {
            byte[] sample = new byte[codecResult.OutputBuffer.remaining()];
            codecResult.OutputBuffer.get(sample);
            long sampleTime = codecResult.bufferInfo.presentationTimeUs;
            int sampleDuration = (int) (sampleTime - lastSampleTime);
            lastSampleTime = sampleTime;

            QueueOfSamples.add(new CodecSample(codecResult.bufferInfo, sample));
            if (IsMaxBufferValue.get())
                if (sampleDuration != 0 &&
                        sample.length > 0 &&
                        sampleTime > sampleDuration * 4) {
                    ExecuteDecoderReadyListeners(sampleDuration, sample.length);
                    onPromiseKept = this::OrderSamples;
                }
            NextPeace();
        };


        Codec.setCallback(new MediaCodec.Callback() {

            @Override
            public void onInputBufferAvailable(@NonNull final MediaCodec mediaCodec,
                                               final int inputBufferId) {
                onInputBufferAvailable.onAvailable(inputBufferId);
            }

            @Override
            public void onOutputBufferAvailable(@NonNull final MediaCodec mediaCodec,
                                                final int outputBufferId,
                                                @NonNull final BufferInfo bufferInfo) {
                KeepOutputPromises(outputBufferId, bufferInfo);
                Codec.releaseOutputBuffer(outputBufferId, false);
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
    }

    public int RemainingBuffers() {
        return InputIdsAvailable.size();
    }

    public int getInputBufferLimit() throws InterruptedException {
        if (bufferLimit == 0) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            this.getInputBufferID(Id -> {
                bufferLimit = getInputBuffer(Id).limit();
                countDownLatch.countDown();
            });
            countDownLatch.await();
        }
        return bufferLimit;
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

    public CodecManager(MediaFormat mediaFormat, boolean IsDecoder) {
        PrepareEndStart(mediaFormat, IsDecoder);
    }

    CodecManager() {
    }

    public MediaFormat getOutputFormat() {
        return Codec.getOutputFormat();
    }

    public synchronized void getInputBufferID(IdListener idListener) {
        if (InputIdsAvailable.size() > 0) {
            int InputId = InputIdsAvailable.get(0);
            InputIdsAvailable.remove(0);
            idListener.onIdAvailable(InputId);
        } else {
            InputIDListeners.add(idListener);
        }
    }

    void processInput(CodecManagerRequest codecManagerRequest) {
        Codec.queueInputBuffer(codecManagerRequest.BufferId,
                codecManagerRequest.bufferInfo.offset,
                codecManagerRequest.bufferInfo.size,
                codecManagerRequest.bufferInfo.presentationTimeUs,
                codecManagerRequest.bufferInfo.flags);
    }

    public void addDecoderReadyListener(ReadyListener readyListener) {
        ReadyListeners.add(readyListener);
    }

    public void removeDecoderReadyListener(ReadyListener readyListener) {
        ReadyListeners.remove(readyListener);
    }

    protected synchronized void addOrderlyOutputPromise(OutputOrderPromise outputOrderPromise) {
        OutputPromises.add(new OutputPromise(outputOrderPromise.SampleTime, onPromiseKept));
        OrderedOutputPromises.add(outputOrderPromise);
    }

    public void addOnFinishListener(CodecFinishListener finishListener) {
        EncoderFinishListeners.add(finishListener);
    }

    public void removeOnFinishListener(CodecFinishListener finishListener) {
        EncoderFinishListeners.remove(finishListener);
    }

    private void executeFinishListeners() {
        for (int i = 0; i < EncoderFinishListeners.size(); i++) {
            CodecFinishListener codecFinishListener = EncoderFinishListeners.get(i);
            codecFinishListener.OnFinish();
        }
    }

    public void stop() {
        IsStopped = true;
    }

    public ByteBuffer getInputBuffer(int inputID) {
        return Codec.getInputBuffer(inputID);
    }

    public interface PromiseResultListener {
        void onKeep(CodecSample codecSample);
    }

    public interface IdListener {
        void onIdAvailable(int Id);
    }

    public interface ReadyListener {
        void OnReady(int SampleDuration, int SampleSize);
    }

    public interface CodecFinishListener {
        void OnFinish();
    }

    public interface InputIDAvailableListener {
        void onAvailable(int InputID);
    }

    public static class OutputPromise {
        long SampleTime;
        OutputListener promiseResultListener;

        public OutputPromise(long SampleTime, OutputListener promiseResultListener) {
            this.SampleTime = SampleTime;
            this.promiseResultListener = promiseResultListener;
        }
    }

    public static class OutputOrderPromise {
        long SampleTime;
        PromiseResultListener promiseResultListener;

        public OutputOrderPromise(long sampleTime, PromiseResultListener promiseResultListener) {
            SampleTime = sampleTime;
            this.promiseResultListener = promiseResultListener;
        }
    }

    public static class CodecSample {
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
    }
}
