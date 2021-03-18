package com.example.spectrumaudiofrequency.MediaDecoder;

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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import static android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC;
import static android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC;
import static com.example.spectrumaudiofrequency.Util.getFileName;
import static com.example.spectrumaudiofrequency.Util.getUriFromResourceId;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public
class AudioDecoder {
    private final boolean SaveCacheEnable;
    private dbAudioDecoderManager dbAudioDecoderManager;
    private final String MediaName;

    public static class PeriodRequest {
        long RequiredTime;

        ProcessListener ProcessListener;

        public PeriodRequest(long RequiredTime, ProcessListener ProcessListener) {
            this.RequiredTime = RequiredTime;
            this.ProcessListener = ProcessListener;
        }
    }

    public static class DecoderResult {
        public byte[] SamplesChannels;
        public final long SampleTime;

        DecoderResult(byte[] SamplesChannels, long sampleTime) {
            this.SamplesChannels = SamplesChannels;
            SampleTime = sampleTime;
        }
    }

    private final ForkJoinPool Poll;

    private MediaCodec Decoder;
    private MediaFormat format;
    private MediaExtractor extractor;

    private Context context;
    private Uri uri;
    private String AudioPath = null;

    public long MediaDuration;
    public int SampleDuration;
    public int ChannelsNumber;
    public int SampleSize;

    public int process = 0;

    private interface IdListener {
        void onIdAvailable(int Id);
    }

    public interface ProcessListener {
        void OnProceed(DecoderResult decoderResult);
    }

    private final ArrayList<Integer> InputIds = new ArrayList<>();
    private final ArrayList<IdListener> InputIDListeners = new ArrayList<>();
    private final ArrayList<PeriodRequest> RequestsPromises = new ArrayList<>();
    private final ArrayList<PeriodRequest> OutputPromises = new ArrayList<>();

    private PeriodRequest getOutputPromise(long RequiredTime) {
        for (int i = 0; i < OutputPromises.size(); i++) {
            if (OutputPromises.get(i).RequiredTime == RequiredTime) {
                PeriodRequest periodRequest = OutputPromises.get(i);
                OutputPromises.remove(i);
                return periodRequest;
            }
        }
        return null;
    }

    public AudioDecoder(Context context, int ResourceId, boolean SaveCacheEnable) {
        this.context = context;

        this.uri = getUriFromResourceId(context, ResourceId);
        this.MediaName = getFileName(context.getResources().getResourceName(ResourceId));

        Log.e("MediaName", MediaName);
        this.SaveCacheEnable = SaveCacheEnable;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.Poll = ForkJoinPool.commonPool();
        } else {
            this.Poll = new ForkJoinPool();
        }
    }

    AudioDecoder(String AudioPath, boolean saveCacheEnable) {
        SaveCacheEnable = saveCacheEnable;
        this.AudioPath = AudioPath;
        this.MediaName = getFileName(AudioPath);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.Poll = ForkJoinPool.commonPool();
        } else {
            this.Poll = new ForkJoinPool();
        }
    }

    public ForkJoinTask<?> prepare() {

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

                    PeriodRequest promise = getOutputPromise(bufferInfo.presentationTimeUs);
                    if (promise == null) {//todo é possivel otput buffer esta avalible mas não ter nenhuma promessa pendente ?
                        //todo provavelmente o extrator compartolhado entre em requisisioes
                        Log.e("OnOutputAvailable", "not are premises bur has a output ?");
                        return;
                    }

                    ByteBuffer outputBuffer = Decoder.getOutputBuffer(outputBufferId);

                    byte[] bytes = new byte[outputBuffer.remaining()];
                    outputBuffer.get(bytes);

                    DecoderResult decoderResult = new DecoderResult(bytes, bufferInfo.presentationTimeUs);

                    if (SaveCacheEnable) {
                        promise.ProcessListener.OnProceed(decoderResult);
                    } else {
                        boolean sampleInvalid = bytes.length < 1;
                        if (sampleInvalid) {
                            Poll.execute(() -> promise.ProcessListener.OnProceed(decoderResult));
                        } else {
                            addRequest(promise);
                        }
                    }

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

            if (SaveCacheEnable) {
                this.dbAudioDecoderManager = new dbAudioDecoderManager(context, MediaName);
                if (!dbAudioDecoderManager.MediaIsDecoded(MediaName)) startDecoding();
            }
        });
    }

    private void next() {
        getInputId(InputID -> {
            int sampleSize = extractor.readSampleData(Decoder.getInputBuffer(InputID), 0);
            if (sampleSize < 0) {
                dbAudioDecoderManager.setDecoded(MediaName);
                return;
            }
            long extractorTime = extractor.getSampleTime();

            process = (int) ((extractorTime / MediaDuration) * 100);
            OutputPromises.add(new PeriodRequest(extractorTime, decoderResult -> {
                if (RequestsPromises.size() > 0) {
                    for (int i = 0; i < RequestsPromises.size(); i++) {
                        PeriodRequest request = RequestsPromises.get(i);

                        dbAudioDecoderManager.addSamplePiece((int)
                                (decoderResult.SampleTime / SampleDuration), decoderResult.SamplesChannels);

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
                    next();
                }
            }));
            Decoder.queueInputBuffer(InputID, 0, sampleSize, extractorTime, 0);
        });
    }

    private void startDecoding() {
        next();
    }

    public void setTimeOnExtractor(PeriodRequest periodRequest) {
        long oldTime = extractor.getSampleTime();

        if (periodRequest.RequiredTime < 0) {
            Log.e("setTimeOfExtractor", "NewTime < 0 ");
            periodRequest.RequiredTime = 0;
        } else if (periodRequest.RequiredTime >= MediaDuration) {
            Log.e("setTimeOfExtractor", "NewTime > MediaDuration");
            extractor.seekTo(MediaDuration, SEEK_TO_PREVIOUS_SYNC);
        } else if (periodRequest.RequiredTime + SampleDuration > MediaDuration) {
            extractor.seekTo(periodRequest.RequiredTime, SEEK_TO_PREVIOUS_SYNC);
        } else if (periodRequest.RequiredTime != oldTime + SampleDuration) {
            extractor.seekTo(periodRequest.RequiredTime, SEEK_TO_CLOSEST_SYNC);
        } else extractor.advance();


        oldTime = extractor.getSampleTime();
        if (oldTime != periodRequest.RequiredTime) {
            Log.e("setTimeOfExtractor",
                    "NewTime is != extractorTime | NewTime: " + periodRequest.RequiredTime
                            + " extractorTime: " + oldTime
                            + " Jump: " + (oldTime - periodRequest.RequiredTime)
                            + " SampleDuration: " + SampleDuration
                            + " Max Duration:" + MediaDuration);

            periodRequest.RequiredTime = extractor.getSampleTime();
        }
    }

    public short[][] bytesToSampleChannels(byte[] bytes) {
        short[] shorts = new short[bytes.length / 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts);

        short[][] SamplesChannels;
        SamplesChannels = new short[ChannelsNumber][shorts.length / ChannelsNumber];
        for (int i = 0; i < SamplesChannels.length; ++i) {
            for (int j = 0; j < SamplesChannels[i].length; j++) {
                SamplesChannels[i][j] = shorts[j * ChannelsNumber + i];
            }
        }

        this.SampleSize = SamplesChannels[0].length;//todo ???
        return SamplesChannels;
    }

    private void putRequest(int InputId, PeriodRequest periodRequest) {
        setTimeOnExtractor(periodRequest);

        int sampleSize = extractor.readSampleData(Decoder.getInputBuffer(InputId), 0);

        if (sampleSize < 0) sampleSize = 1;
        OutputPromises.add(periodRequest);
        Decoder.queueInputBuffer(InputId, 0, sampleSize, extractor.getSampleTime(), 0);
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
        if (SaveCacheEnable) {
            int SamplePeace = (int) (periodRequest.RequiredTime / SampleDuration);
            byte[] bytes = dbAudioDecoderManager.getSamplePiece(SamplePeace);

            if (bytes != null) {
                periodRequest.ProcessListener.OnProceed(new DecoderResult(bytes,SamplePeace));
                return;
            } else {
                RequestsPromises.add(periodRequest);
            }
        }
        getInputId(InputID -> putRequest(InputID, periodRequest));
    }
}