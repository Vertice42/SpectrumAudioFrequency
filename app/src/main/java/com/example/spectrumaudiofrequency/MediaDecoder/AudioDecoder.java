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

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public
class AudioDecoder {
    public static class PeriodRequest {
        long RequiredTime;
        long RequiredSampleDuration;

        ProcessListener ProcessListener;

        public PeriodRequest(long RequiredTime, long RequiredSampleDuration, ProcessListener ProcessListener) {
            this.RequiredTime = RequiredTime;
            this.RequiredSampleDuration = RequiredSampleDuration;
            this.ProcessListener = ProcessListener;
        }
    }

    public static class DecoderResult {
        public short[][] SamplesChannels;
        public long SampleDuration;
        public long SampleTime;

        DecoderResult(short[][] SamplesChannels, long SampleTime, long SampleDuration) {
            this.SamplesChannels = SamplesChannels;
            this.SampleDuration = SampleDuration;
            this.SampleTime = SampleTime;
        }
    }

    private ForkJoinPool Poll;

    private MediaCodec Decoder;
    private MediaFormat format;
    private MediaExtractor extractor;

    private Context context;
    private Uri uri;
    private String AudioPath;

    public long MediaDuration;
    public int SampleDuration;
    public int ChannelsNumber;
    public int SampleSize;

    private interface IdListener {
        void onIdAvailable(int Id);
    }

    public interface ProcessListener {
        void OnProceed(DecoderResult decoderResult);
    }

    private final ArrayList<Integer> InputIds = new ArrayList<>();
    private final ArrayList<IdListener> InputIDListeners = new ArrayList<>();
    private final ArrayList<PeriodRequest> RequestsPromises = new ArrayList<>();

    private PeriodRequest getRequestPromise(long RequiredTime) {
        for (int i = 0; i < RequestsPromises.size(); i++) {
            if (RequestsPromises.get(i).RequiredTime == RequiredTime) {
                PeriodRequest periodRequest = RequestsPromises.get(i);
                RequestsPromises.remove(i);
                return periodRequest;
            }
        }
        return null;
    }

    public AudioDecoder(Context context, Uri uri) {
        this.context = context;
        this.uri = uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.Poll = ForkJoinPool.commonPool();
        } else {
            this.Poll = new ForkJoinPool();
        }
    }

    AudioDecoder(String AudioPath) {
        this.AudioPath = AudioPath;
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

                    PeriodRequest requestPromise = getRequestPromise(bufferInfo.presentationTimeUs);
                    DecoderResult decoderResult = processOutput(outputBufferId);

                    if (requestPromise == null) {//todo é possivel otput buffer esta avalible mas não ter nenhuma promessa pendente ?
                        //todo provavelmente o extrator compartolhado entre em requisisioes
                        Log.e("OnOutputAvailable", "not are premises bur has a output ?");
                        return;
                    }
                    if (decoderResult.SamplesChannels[0].length < 1) {
                        Poll.execute(() -> addRequest(requestPromise));
                    } else
                        Poll.execute(() -> {
                            requestPromise.ProcessListener.OnProceed(decoderResult);
                        });
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

    private DecoderResult processOutput(int OutputId) {

        ByteBuffer outputBuffer = Decoder.getOutputBuffer(OutputId);
        short[][] SamplesChannels;

        ShortBuffer buffer = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer();

        SamplesChannels = new short[ChannelsNumber][buffer.remaining() / ChannelsNumber];
        for (int i = 0; i < SamplesChannels.length; ++i) {
            for (int j = 0; j < SamplesChannels[i].length; j++) {
                SamplesChannels[i][j] = buffer.get(j * ChannelsNumber + i);
            }
        }

        this.SampleSize = SamplesChannels[0].length;
        //separate channels

        Decoder.releaseOutputBuffer(OutputId, false);

        return new DecoderResult(SamplesChannels, extractor.getSampleTime(), SampleDuration);
    }

    private void putRequest(int InputId, PeriodRequest periodRequest) {
        setTimeOnExtractor(periodRequest);
        int sampleSize = extractor.readSampleData(Decoder.getInputBuffer(InputId), 0);

        if (sampleSize < 0) sampleSize = 1;
        RequestsPromises.add(periodRequest);
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
        getInputId(InputID -> putRequest(InputID, periodRequest));
    }
}