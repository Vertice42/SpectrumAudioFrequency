package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

public class CodecManager {
    interface IdListener {
        void onIdAvailable(int Id);
    }

    public static class CodecManagerResult {
        public ByteBuffer OutputBuffer;
        public MediaCodec.BufferInfo bufferInfo;

        public CodecManagerResult(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo) {
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
    }//todo simplifly ?

    public interface OutputListener {
        void OnProceed(CodecManagerResult codecResult);
    }

    static class OutputListenerCustum {
        boolean IsConsumable = false;
        OutputListener outputListener;

        public OutputListenerCustum(boolean isConsumable, OutputListener outputListener) {
            IsConsumable = isConsumable;
            this.outputListener = outputListener;
        }
    }

    public static class CodecManagerRequest {
        public final int BufferId;
        public final MediaCodec.BufferInfo bufferInfo;

        public CodecManagerRequest(int bufferId, MediaCodec.BufferInfo bufferInfo) {
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

    private MediaCodec Codec = null;
    private int bufferLimit = 0;

    public MediaFormat mediaFormat;
    public long MediaDuration;
    public int EncoderPadding = 0;

    public final ArrayList<Integer> InputIds = new ArrayList<>();
    public final ArrayList<IdListener> InputIDListeners = new ArrayList<>();
    public final ArrayList<OutputListenerCustum> outputListenersCustum = new ArrayList<>();

    public static MediaFormat copyMediaFormat(MediaFormat mediaFormat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new MediaFormat(mediaFormat);
        } else {
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);

            MediaFormat r = null;

            if (mime.contains("video")) {
                int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
                int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                r = MediaFormat.createVideoFormat(mime, width, height);

                r.setInteger(MediaFormat.KEY_FRAME_RATE, mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE));
                if (r.containsKey(MediaFormat.KEY_CAPTURE_RATE))
                    r.setInteger(MediaFormat.KEY_CAPTURE_RATE, mediaFormat.getInteger(MediaFormat.KEY_CAPTURE_RATE));

            } else if (mime.contains("audio")) {
                int channelCount = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                int sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                r = MediaFormat.createAudioFormat(mime, sampleRate, channelCount);
            }

            assert r != null;
            r.setLong(MediaFormat.KEY_DURATION, mediaFormat.getLong(MediaFormat.KEY_DURATION));
            r.setInteger(MediaFormat.KEY_BIT_RATE, mediaFormat.getInteger(MediaFormat.KEY_BIT_RATE));

            return r;
        }
    }

    public MediaFormat getOutputFormat() {
        return Codec.getOutputFormat();
    }

    public void GiveBackBufferId(int BufferId) {
        if (InputIDListeners.size() != 0) {
            IdListener idListener = InputIDListeners.get(0);
            idListener.onIdAvailable(BufferId);
            InputIDListeners.remove(idListener);
        } else {
            InputIds.add(BufferId);
        }
    }

    private void executeOutputListeners(int outputBufferId, MediaCodec.BufferInfo bufferInfo) {
        for (int i = 0; i < outputListenersCustum.size(); i++) {
            OutputListenerCustum listener = outputListenersCustum.get(i);
            listener.outputListener.OnProceed(new CodecManagerResult
                    (Codec.getOutputBuffer(outputBufferId), bufferInfo));
            if (listener.IsConsumable) removeOnOutput(listener);
        }
    }

    public CodecManager(MediaFormat mediaFormat, boolean IsDecoder) {
        PrepareEndStart(mediaFormat, IsDecoder);
    }

    CodecManager() {
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

    void PrepareEndStart(MediaFormat mediaFormat, boolean IsDecoder) {
        // todo adicionar multidetherd ou loding
        try {
            this.mediaFormat = mediaFormat;

            if (IsDecoder) {
                Codec = MediaCodec.createDecoderByType(mediaFormat.getString(android.media.MediaFormat.KEY_MIME));
            } else {
                Codec = MediaCodec.createEncoderByType(mediaFormat.getString(android.media.MediaFormat.KEY_MIME));
            }

            String ENCODER_PADDING = "encoder-padding";
            if (mediaFormat.containsKey(ENCODER_PADDING))
                EncoderPadding = mediaFormat.getInteger(ENCODER_PADDING);
            MediaDuration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
            Codec.setCallback(new MediaCodec.Callback() {

                @Override
                public void onInputBufferAvailable(@NonNull final MediaCodec mediaCodec,
                                                   final int inputBufferId) {
                    GiveBackBufferId(inputBufferId);
                }

                @Override
                public void onOutputBufferAvailable(@NonNull final MediaCodec mediaCodec,
                                                    final int outputBufferId,
                                                    @NonNull final MediaCodec.BufferInfo bufferInfo) {
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
                    Log.i("CodecManager", "onOutputFormatChanged: " + mediaFormat.toString());
                }
            });

            if (IsDecoder) {
                Codec.configure(mediaFormat, null, null, 0);
            } else {
                Codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            }

            Codec.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public void putInput(CodecManagerRequest codecManagerRequest) {
        Codec.queueInputBuffer(codecManagerRequest.BufferId,
                codecManagerRequest.bufferInfo.offset,
                codecManagerRequest.bufferInfo.size,
                codecManagerRequest.bufferInfo.presentationTimeUs,
                codecManagerRequest.bufferInfo.flags);
    }

    public void addOnOutputListener(OutputListener outputListener) {
        outputListenersCustum.add(new OutputListenerCustum(false, outputListener));
    }

    public void addOnOutputListenerConsumable(OutputListener outputListener) {
        outputListenersCustum.add(new OutputListenerCustum(true, outputListener));
    }

    public void removeOnOutput(OutputListenerCustum outputListenerCustum) {
        outputListenersCustum.remove(outputListenerCustum);
    }
}
