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

import static android.media.MediaCodec.CONFIGURE_FLAG_ENCODE;
import static android.media.MediaFormat.KEY_BIT_RATE;
import static android.media.MediaFormat.KEY_CAPTURE_RATE;
import static android.media.MediaFormat.KEY_DURATION;
import static android.media.MediaFormat.KEY_FRAME_RATE;

public class CodecManager {

    public interface OutputListener {
        void OnProceed(CodecManagerResult codecResult);
    }

    interface IdListener {
        void onIdAvailable(int Id);
    }

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

    public static class OutputPromise {
        long SampleTime;
        OutputListener outputListener;

        public OutputPromise(long sampleTime, OutputListener outputListener) {
            SampleTime = sampleTime;
            this.outputListener = outputListener;
        }
    }

    private ForkJoinPool forkJoinPool;
    private MediaCodec Codec = null;
    private int bufferLimit = 0;

    public MediaFormat mediaFormat;
    public long MediaDuration;
    public int EncoderPadding = 0;

    public final ArrayList<Integer> InputIds = new ArrayList<>();
    public final ArrayList<IdListener> InputIDListeners = new ArrayList<>();
    public final ArrayList<OutputListener> outputListeners = new ArrayList<>();
    public final ArrayList<OutputPromise> outputPromises = new ArrayList<>();

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

    public void GiveBackInputID(int BufferId) {
        forkJoinPool.execute(() -> {
            if (InputIDListeners.size() != 0) {
                IdListener idListener = InputIDListeners.get(0);
                idListener.onIdAvailable(BufferId);
                InputIDListeners.remove(idListener);
            } else InputIds.add(BufferId);
        });
    }

    private synchronized void executeOutputListeners(int outputBufferId, BufferInfo bufferInfo) {
        //Log.i("outputListeners" + this.getClass().getSimpleName(), outputListeners.size() + "");
        for (int i = 0; i < outputListeners.size(); i++) {
            OutputListener listener = outputListeners.get(i);
            listener.OnProceed(new CodecManagerResult
                    (Codec.getOutputBuffer(outputBufferId), bufferInfo));
        }
        int i = 0;
        while (i < outputPromises.size()) {
            OutputPromise outputPromise = outputPromises.get(i);
            if (outputPromise.SampleTime == bufferInfo.presentationTimeUs) {
                outputPromise.outputListener.OnProceed(
                        new CodecManagerResult(Codec.getOutputBuffer(outputBufferId), bufferInfo));
                outputPromises.remove(outputPromise);
            }else i++;
        }
        // Log.i("Codec." + this.getClass().getSimpleName(), "InputIds available: " + InputIds.size() + " inputs awaiting: " + outputListeners.size());
    }

    void PrepareEndStart(MediaFormat mediaFormat, boolean IsDecoder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            this.forkJoinPool = ForkJoinPool.commonPool();
        else this.forkJoinPool = new ForkJoinPool();
        try {
            this.mediaFormat = mediaFormat;

            if (IsDecoder) {
                Codec = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
            } else {
                Codec = MediaCodec.createEncoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
            }

            String ENCODER_PADDING = "encoder-padding";
            if (mediaFormat.containsKey(ENCODER_PADDING))
                EncoderPadding = mediaFormat.getInteger(ENCODER_PADDING);
            MediaDuration = mediaFormat.getLong(KEY_DURATION);
            Codec.setCallback(new MediaCodec.Callback() {

                @Override
                public void onInputBufferAvailable(@NonNull final MediaCodec mediaCodec,
                                                   final int inputBufferId) {
                    GiveBackInputID(inputBufferId);
                }

                @Override
                public void onOutputBufferAvailable(@NonNull final MediaCodec mediaCodec,
                                                    final int outputBufferId,
                                                    @NonNull final BufferInfo bufferInfo) {
                    executeOutputListeners(outputBufferId, bufferInfo);
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

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getInputBufferLimit() throws InterruptedException {
        if (bufferLimit == 0) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            this.getInputBufferId(Id -> {
                bufferLimit = getInputBuffer(Id).limit();
                countDownLatch.countDown();
            });
            countDownLatch.await();
        }
        return bufferLimit;
    }

    public void getInputBufferId(IdListener idListener) {
        if (InputIds.size() > 0) {
            int InputId = InputIds.get(0);
            InputIds.remove(0);
            idListener.onIdAvailable(InputId);
        } else {
            InputIDListeners.add(idListener);
        }
    }

    public ByteBuffer getInputBuffer(int inputID) {
        return Codec.getInputBuffer(inputID);
    }

    public void processInput(CodecManagerRequest codecManagerRequest) {
        Codec.queueInputBuffer(codecManagerRequest.BufferId,
                codecManagerRequest.bufferInfo.offset,
                codecManagerRequest.bufferInfo.size,
                codecManagerRequest.bufferInfo.presentationTimeUs,
                codecManagerRequest.bufferInfo.flags);
    }

    public void addOnOutputListener(OutputListener outputListener) {
        outputListeners.add(outputListener);
    }

    public void removeOnOutputListener(OutputListener outputListener) {
        outputListeners.remove(outputListener);
    }

    public synchronized void addOnOutputPromise(OutputPromise outputPromise) {

        outputPromises.add(outputPromise);
    }
}
