package com.example.spectrumaudiofrequency.core.codec_manager;


import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;

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

    public interface ProcessListener {
        void OnProceed(CodecManagerResult codecResult);
    }

    public static class CodecManagerRequest {
        public final int BufferId;
        public final MediaCodec.BufferInfo bufferInfo;
        ProcessListener ProcessListener;

        public CodecManagerRequest(int bufferId, MediaCodec.BufferInfo bufferInfo, CodecManager.ProcessListener processListener) {
            BufferId = bufferId;
            this.bufferInfo = bufferInfo;
            ProcessListener = processListener;
        }
    }

    private MediaCodec Codec = null;
    public MediaFormat mediaFormat;

    public final ArrayList<Integer> InputIds = new ArrayList<>();
    public final ArrayList<IdListener> InputIDListeners = new ArrayList<>();
    public final ArrayList<CodecManagerRequest> OutputPromises = new ArrayList<>();

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

            // if (mediaFormat.containsKey("csd-0")) r.setByteBuffer("csd-0", mediaFormat.getByteBuffer("csd-0"));

            return r;
        }
    }

    public CodecManagerRequest getOutputPromise(int BufferId) {
        for (int i = 0; i < OutputPromises.size(); i++) {
            if (OutputPromises.get(i).BufferId == BufferId) {
                CodecManagerRequest codecManagerRequest = OutputPromises.get(i);
                OutputPromises.remove(i);
                return codecManagerRequest;
            }
        }
        return null;
    }

    public MediaFormat getOutputFormat() {
        return Codec.getOutputFormat();
    }

    long MediaDuration = 1;

    public CodecManager(MediaFormat mediaFormat, boolean IsDecoder) {
        // todo adicionar multidetherd ou loding
        try {
            this.mediaFormat = mediaFormat;

            if (IsDecoder) {
                Codec = MediaCodec.createDecoderByType(mediaFormat.getString(android.media.MediaFormat.KEY_MIME));
            } else {
                Codec = MediaCodec.createEncoderByType(mediaFormat.getString(android.media.MediaFormat.KEY_MIME));
            }

            MediaDuration = mediaFormat.getLong(MediaFormat.KEY_DURATION);

            Codec.setCallback(new MediaCodec.Callback() {

                @Override
                public void onInputBufferAvailable(@NonNull final MediaCodec mediaCodec,
                                                   final int inputBufferId) {
                    if (InputIDListeners.size() != 0) {
                        IdListener idListener = InputIDListeners.get(0);
                        idListener.onIdAvailable(inputBufferId);
                        InputIDListeners.remove(idListener);
                    } else {
                        InputIds.add(inputBufferId);
                    }
                }

                @Override
                public void onOutputBufferAvailable(@NonNull final MediaCodec mediaCodec,
                                                    final int outputBufferId,
                                                    @NonNull final MediaCodec.BufferInfo bufferInfo) {
                    CodecManagerRequest promise = getOutputPromise(outputBufferId);
                    if (promise == null) {
                        Log.e("OnOutputAvailable", "Not are promises but are a output");
                    } else {
                        promise.ProcessListener.OnProceed(new CodecManagerResult(Codec.getOutputBuffer(outputBufferId), bufferInfo));
                    }
                    float TotalAverageDurationProcessed = (((float) bufferInfo.presentationTimeUs / MediaDuration) * 100f);

                    String CodecType = "";
                    if (IsDecoder) CodecType = "Decoder";
                    else CodecType = "Encoder";
                    Log.i("Codec " + CodecType, "Process: " + TotalAverageDurationProcessed + "% " + "presentationTimeUs" + bufferInfo.presentationTimeUs);
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

    public void processInput(CodecManagerRequest codecManagerRequest) {
        OutputPromises.add(codecManagerRequest);
        Codec.queueInputBuffer(codecManagerRequest.BufferId,
                codecManagerRequest.bufferInfo.offset,
                codecManagerRequest.bufferInfo.size,
                codecManagerRequest.bufferInfo.presentationTimeUs,
                codecManagerRequest.bufferInfo.flags);
    }

    public void putConfig(int BufferId, int bufferSize) {
        Codec.queueInputBuffer(BufferId, 0, bufferSize, 0,
                MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
    }

}
