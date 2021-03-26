package com.example.spectrumaudiofrequency.mediaDecoder;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import static android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC;
import static com.example.spectrumaudiofrequency.util.Files.getFileName;
import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public
class AudioDecoder {

    public static class PeriodRequest {
        long RequiredTime;
        ProcessListener ProcessListener;

        public PeriodRequest(long RequiredTime, ProcessListener ProcessListener) {
            this.RequiredTime = RequiredTime;
            this.ProcessListener = ProcessListener;
        }
    }

    public static class OutputPromise extends PeriodRequest {
        int Id;

        public OutputPromise(int Id, long RequiredTime, AudioDecoder.ProcessListener ProcessListener) {
            super(RequiredTime, ProcessListener);
            this.Id = Id;
        }

        public OutputPromise(int Id, PeriodRequest periodRequest) {
            super(periodRequest.RequiredTime, periodRequest.ProcessListener);
            this.Id = Id;
        }
    }

    public static class DecoderResult {
        public byte[] BytesSamplesChannels;
        public final long SampleTime;

        DecoderResult(byte[] BytesSamplesChannels, long sampleTime) {
            this.BytesSamplesChannels = BytesSamplesChannels;
            SampleTime = sampleTime;
        }

        public short[][] getSampleChannels(AudioDecoder audioDecoder) {
            short[] shorts = new short[BytesSamplesChannels.length / 2];
            ByteBuffer.wrap(BytesSamplesChannels).order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts);

            short[][] SamplesChannels;
            SamplesChannels = new short[audioDecoder.ChannelsNumber][shorts.length / audioDecoder.ChannelsNumber];
            for (int i = 0; i < SamplesChannels.length; ++i) {
                for (int j = 0; j < SamplesChannels[i].length; j++) {
                    SamplesChannels[i][j] = shorts[j * audioDecoder.ChannelsNumber + i];
                }
            }

            if (SamplesChannels[0].length < 1) SamplesChannels = new short[2][200];
            return SamplesChannels;
        }

        @Override
        public @NotNull String toString() {
            return "DecoderResult{" +
                    "BytesSamplesChannels=" + Arrays.toString(BytesSamplesChannels) +
                    ", SampleTime=" + SampleTime +
                    '}';
        }
    }

    public long MediaDuration;
    public int SampleDuration;
    public int ChannelsNumber;

    public float TotalAverageDurationProcessed = 0;

    private interface IdListener {
        void onIdAvailable(int Id);
    }

    public interface ProcessListener {
        void OnProceed(DecoderResult decoderResult);
    }

    private dbAudioDecoderManager dbAudioDecoderManager;
    private final String MediaName;

    private final ForkJoinPool Poll;

    private MediaCodec Decoder;
    private MediaFormat format;
    private MediaExtractor extractor;

    private Context context;
    private Uri uri;
    private String AudioPath = null;

    private final ArrayList<Integer> InputIds = new ArrayList<>();
    private final ArrayList<IdListener> InputIDListeners = new ArrayList<>();
    private final ArrayList<PeriodRequest> RequestsPromises = new ArrayList<>();
    private final ArrayList<OutputPromise> OutputPromises = new ArrayList<>();

    private PeriodRequest getOutputPromise(int Id) {
        for (int i = 0; i < OutputPromises.size(); i++) {
            if (OutputPromises.get(i).Id == Id) {
                PeriodRequest periodRequest = OutputPromises.get(i);
                OutputPromises.remove(i);
                return periodRequest;
            }
        }
        return null;
    }

    public AudioDecoder(Context context, int ResourceId) {
        this.context = context;

        this.uri = getUriFromResourceId(context, ResourceId);
        this.MediaName = getFileName(context.getResources().getResourceName(ResourceId));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.Poll = ForkJoinPool.commonPool();
        } else {
            this.Poll = new ForkJoinPool();
        }
    }

    public AudioDecoder(String AudioPath) {
        this.AudioPath = AudioPath;
        this.MediaName = getFileName(AudioPath);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.Poll = ForkJoinPool.commonPool();
        } else {
            this.Poll = new ForkJoinPool();
        }
    }

    public ForkJoinTask<?> prepare() {
        // todo adicionar multidetherd ou loding
        return this.Poll.submit(() -> {
            try {
                Decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_MPEG);
            } catch (IOException e) {
                e.printStackTrace();//todo add e
            }

            extractor = new MediaExtractor();
            try {
                if (AudioPath != null) extractor.setDataSource(AudioPath);
                else extractor.setDataSource(context, uri, null);
            } catch (IOException e) {
                e.printStackTrace();
            }

            format = extractor.getTrackFormat(0);

            MediaDuration = format.getLong(MediaFormat.KEY_DURATION);
            ChannelsNumber = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            SampleDuration = format.getInteger(MediaFormat.KEY_SAMPLE_RATE) / ChannelsNumber;

            extractor.selectTrack(0);

            Decoder.setCallback(new MediaCodec.Callback() {

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
                                                    @NonNull final BufferInfo bufferInfo) {
                    PeriodRequest promise = getOutputPromise(outputBufferId);
                    if (promise == null) {
                        Log.e("OnOutputAvailable", "not are premises bur has a output ?");
                        return;
                    }

                    ByteBuffer outputBuffer = Decoder.getOutputBuffer(outputBufferId);

                    byte[] bytes = new byte[outputBuffer.remaining()];
                    outputBuffer.get(bytes);

                    DecoderResult decoderResult = new DecoderResult(bytes, bufferInfo.presentationTimeUs);

                    promise.ProcessListener.OnProceed(decoderResult);

                    Decoder.releaseOutputBuffer(outputBufferId, false);
                }

                @Override
                public void onError(@NonNull final MediaCodec mediaCodec,
                                    @NonNull final MediaCodec.CodecException e) {
                    Log.e("MediaCodecERROR", "onError: ", e);
                }

                @Override
                public void onOutputFormatChanged(@NonNull final MediaCodec mediaCodec,
                                                  @NonNull final MediaFormat mediaFormat) {
                }
            });
            Decoder.configure(format, null, null, 0);
            Decoder.start();

            this.dbAudioDecoderManager = new dbAudioDecoderManager(context, MediaName);
            if (!dbAudioDecoderManager.MediaIsDecoded(MediaName)) startDecoding();

        });
    }

    private void next() {
        getInputId(InputID -> {
            int sampleSize = extractor.readSampleData(Decoder.getInputBuffer(InputID), 0);
            if (sampleSize < 0) {
                if (TotalAverageDurationProcessed < 99) {
                    Log.i("Average Error", "TotalAverageDurationProcessed: " + TotalAverageDurationProcessed);
                }
                dbAudioDecoderManager.setDecoded(MediaName);
                return;
            }
            long extractorTime = extractor.getSampleTime();

            TotalAverageDurationProcessed = (((float) extractorTime / MediaDuration) * 100f);
            Log.i("Processed", "TotalAverageDurationProcessed: " + TotalAverageDurationProcessed + "%");

            OutputPromises.add(new OutputPromise(InputID, extractorTime, decoderResult -> {
                dbAudioDecoderManager.addSamplePiece(
                        (int) (decoderResult.SampleTime / SampleDuration),
                        decoderResult.BytesSamplesChannels);

                if (RequestsPromises.size() > 0) {
                    for (int i = 0; i < RequestsPromises.size(); i++) {
                        PeriodRequest request = RequestsPromises.get(i);

                        if (request.RequiredTime == decoderResult.SampleTime) {
                            request.ProcessListener.OnProceed(decoderResult);
                            RequestsPromises.remove(request);
                            break;
                        }

                        if (request.RequiredTime < decoderResult.SampleTime) {
                            RequestsPromises.remove(request);
                            addRequest(request);
                            break;
                        }
                    }
                }

                extractor.advance();
                next();
            }));
            Decoder.queueInputBuffer(InputID, 0, sampleSize, extractorTime, 0);
        });
    }

    private void startDecoding() {
        getInputId(InputID -> {
            int sampleSize = extractor.readSampleData(Decoder.getInputBuffer(InputID), 0);
            OutputPromises.add(new OutputPromise(InputID, 0, decoderResult -> {
                extractor.seekTo(0, SEEK_TO_CLOSEST_SYNC);
                next();
            }));
            Decoder.queueInputBuffer(InputID, 0, sampleSize, 0, 0);
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

    public void addRequest(PeriodRequest periodRequest) {
        long LastPeaceTime = MediaDuration - SampleDuration;
        if (periodRequest.RequiredTime > LastPeaceTime)
            periodRequest.RequiredTime = LastPeaceTime;
        else if (periodRequest.RequiredTime < 0)
            periodRequest.RequiredTime = 0;

        int SamplePeace = (int) (periodRequest.RequiredTime / SampleDuration);
        byte[] dbSampleBytes = dbAudioDecoderManager.getSamplePiece(SamplePeace);

        if (dbSampleBytes != null) {
            periodRequest.ProcessListener.OnProceed(new DecoderResult(dbSampleBytes,
                    SamplePeace * SampleDuration));
        } else {
            RequestsPromises.add(periodRequest);
        }

    }

    public void clear() {
        dbAudioDecoderManager.deleteMediaDecoded(MediaName);
    }

    public void destroy() {
        dbAudioDecoderManager.close();
    }
}