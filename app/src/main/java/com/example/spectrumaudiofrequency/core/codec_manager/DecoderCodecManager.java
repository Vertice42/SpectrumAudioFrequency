package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.CodecManagerRequest;
import com.example.spectrumaudiofrequency.core.codec_manager.media_decoder.dbAudioDecoderManager;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

import static android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC;
import static com.example.spectrumaudiofrequency.util.Files.getFileName;
import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DecoderCodecManager {
    public interface ProcessListener {
        void OnProceed(DecoderResult decoderResult);
    }

    public static class DecoderResult {
        public byte[] BytesSamplesChannels;
        public final long SampleTime;

        DecoderResult(byte[] BytesSamplesChannels, long sampleTime) {
            this.BytesSamplesChannels = BytesSamplesChannels;
            SampleTime = sampleTime;
        }

        public short[][] getSampleChannels(DecoderCodecManager decoderCodecManager) {
            short[] shorts = new short[BytesSamplesChannels.length / 2];
            ByteBuffer.wrap(BytesSamplesChannels).order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts);

            short[][] SamplesChannels;
            SamplesChannels = new short[decoderCodecManager.ChannelsNumber][shorts.length / decoderCodecManager.ChannelsNumber];
            for (int i = 0; i < SamplesChannels.length; ++i) {
                for (int j = 0; j < SamplesChannels[i].length; j++) {
                    SamplesChannels[i][j] = shorts[j * decoderCodecManager.ChannelsNumber + i];
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

    public static class PeriodRequest {
        long RequiredTime;
        ProcessListener ProcessListener;

        public PeriodRequest(long RequiredTime, ProcessListener ProcessListener) {
            this.RequiredTime = RequiredTime;
            this.ProcessListener = ProcessListener;
        }
    }

    private dbAudioDecoderManager dbAudioDecoderManager;
    private final ArrayList<PeriodRequest> RequestsPromises = new ArrayList<>();

    public float TotalAverageDurationProcessed = 0;
    public long MediaDuration;
    private int ChannelsNumber;
    public int SampleDuration;

    private final String MediaName;
    private CodecManager codecManager;
    private MediaExtractor extractor;

    private Context context;
    private Uri uri;
    private String AudioPath = null;

    public DecoderCodecManager(Context context, int ResourceId) {
        this.context = context;
        this.uri = getUriFromResourceId(context, ResourceId);
        this.MediaName = getFileName(context.getResources().getResourceName(ResourceId));

        prepare();
    }

    public DecoderCodecManager(String AudioPath) {
        this.AudioPath = AudioPath;
        this.MediaName = getFileName(AudioPath);

        prepare();
    }

    private void prepare() {
        // todo adicionar multidetherd ou loding
        MediaCodec mediaCodec = null;
        try {
            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_MPEG);

            extractor = new MediaExtractor();
            if (AudioPath != null) extractor.setDataSource(AudioPath);
            else extractor.setDataSource(context, uri, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        MediaFormat Format = extractor.getTrackFormat(0);
        extractor.selectTrack(0);

        MediaDuration = Format.getLong(MediaFormat.KEY_DURATION);
        ChannelsNumber = Format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        SampleDuration = Format.getInteger(MediaFormat.KEY_SAMPLE_RATE) / ChannelsNumber;


        codecManager = new CodecManager(mediaCodec, Format);

        this.dbAudioDecoderManager = new dbAudioDecoderManager(context, MediaName);
        if (!dbAudioDecoderManager.MediaIsDecoded(MediaName)) startDecoding();
    }

    private void next() {
        codecManager.getInputBufferId(ID -> {
            int sampleSize = extractor.readSampleData(codecManager.getInputBuffer(ID), 0);
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

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int offset = 0;
            bufferInfo.set(offset, sampleSize, extractor.getSampleTime(), extractor.getSampleFlags());

            codecManager.processInput(new CodecManagerRequest(ID, bufferInfo, decoderResult -> {
                byte[] BytesSamplesChannels = new byte[decoderResult.OutputBuffer.remaining()];
                decoderResult.OutputBuffer.get(BytesSamplesChannels);
                dbAudioDecoderManager.addSamplePiece(
                        (int) (decoderResult.bufferInfo.presentationTimeUs / SampleDuration),
                        BytesSamplesChannels);

                if (RequestsPromises.size() > 0) {
                    for (int i = 0; i < RequestsPromises.size(); i++) {
                        PeriodRequest request = RequestsPromises.get(i);

                        if (request.RequiredTime == decoderResult.bufferInfo.presentationTimeUs) {

                            request.ProcessListener.OnProceed(new DecoderResult(BytesSamplesChannels,
                                    decoderResult.bufferInfo.presentationTimeUs));
                            RequestsPromises.remove(request);
                            break;
                        }

                        if (request.RequiredTime < decoderResult.bufferInfo.presentationTimeUs) {
                            RequestsPromises.remove(request);
                            addRequest(request);
                            break;
                        }
                    }
                }

                extractor.advance();
                next();
            }));
        });
    }

    private void startDecoding() {
        codecManager.getInputBufferId(InputID -> {
            int sampleSize = extractor.readSampleData(codecManager.getInputBuffer(InputID), 0);

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.set(0, sampleSize, extractor.getSampleTime(),
                    extractor.getSampleFlags());

            codecManager.processInput(new CodecManagerRequest(InputID, bufferInfo, decoderResult -> {
                extractor.seekTo(0, SEEK_TO_CLOSEST_SYNC);//todo talves isso sehá desnecesario
                next();
            }));
        });
    }

    public void addRequest(PeriodRequest periodRequest) {
        long LastPeaceTime = MediaDuration - SampleDuration;

        long RequiredTime = periodRequest.RequiredTime;

        if (RequiredTime > LastPeaceTime)
            RequiredTime = LastPeaceTime;
        else if (RequiredTime < 0)
            RequiredTime = 0;

        int SamplePeace = (int) (RequiredTime / SampleDuration);
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