package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.example.spectrumaudiofrequency.core.ByteQueue;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;
import static com.example.spectrumaudiofrequency.util.Files.getFileName;
import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DecoderCodec extends CodecManager {

    public interface DecoderEndListener {
        void OnDecoderEnd();
    }

    public interface DecoderProcessListener {
        void OnProceed(DecoderResult decoderResult);
    }

    public static class DecoderResult extends CodecManagerResult {
        public int SampleId = -1;
        public byte[] Sample;
        public BufferInfo bufferInfo;

        public DecoderResult(int SampleId, byte[] sample, BufferInfo bufferInfo) {
            super(null, bufferInfo);
            this.SampleId = SampleId;
            Sample = sample;
            this.bufferInfo = bufferInfo;
        }

        public DecoderResult() {
            super(null, null);
        }

        public boolean SampleTimeNotExist() {
            return (bufferInfo == null);
        }

        public short[][] getSampleChannels(DecoderCodec decoderCodecWithCacheManager) {
            short[] shorts = new short[Sample.length / 2];
            ByteBuffer.wrap(Sample).order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts);

            short[][] SamplesChannels;
            SamplesChannels = new short[decoderCodecWithCacheManager.ChannelsNumber]
                    [shorts.length / decoderCodecWithCacheManager.ChannelsNumber];
            for (int i = 0; i < SamplesChannels.length; ++i) {
                for (int j = 0; j < SamplesChannels[i].length; j++) {
                    SamplesChannels[i][j] = shorts[j * decoderCodecWithCacheManager.ChannelsNumber + i];
                }
            }

            if (SamplesChannels[0].length < 1) SamplesChannels = new short[2][200];
            return SamplesChannels;
        }

        @Override
        public @NotNull String toString() {
            return "DecoderResult{" +
                    "BsChannels=" + Arrays.toString(Sample) +
                    ", presentationTimeUs=" + bufferInfo.presentationTimeUs +
                    '}';
        }
    }

    public static class PeriodRequest {
        int RequiredSampleId;
        DecoderProcessListener DecoderListener;

        public PeriodRequest(int RequiredSampleId, DecoderProcessListener DecoderListener) {
            this.RequiredSampleId = RequiredSampleId;
            this.DecoderListener = DecoderListener;
        }
    }

    private final ArrayList<DecoderProcessListener> decoderListeners = new ArrayList<>();
    private final ArrayList<DecoderEndListener> FinishListeners = new ArrayList<>();

    final Context context;
    final String MediaName;

    private Uri uri;
    private String AudioPath = null;

    private MediaExtractor extractor;

    private long TrueMediaDuration;
    private boolean IsStarted = false;
    boolean WasDecoded = false;

    public int ChannelsNumber;
    public int NewSampleDuration = 0;

    public DecoderCodec(Context context, int ResourceId) {
        super();
        this.context = context;
        this.uri = getUriFromResourceId(context, ResourceId);
        this.MediaName = getFileName(context.getResources().getResourceName(ResourceId));
        prepare();
    }

    public DecoderCodec(Context context, String AudioPath) {
        super();
        this.context = context;
        this.AudioPath = AudioPath;
        this.MediaName = getFileName(AudioPath);
        prepare();
    }

    public boolean IsStarted() {
        return IsStarted;
    }

    public int getSampleLength() {
        int Length = (int) Math.ceil(TrueMediaDuration() / (double) NewSampleDuration);
        if (WasDecoded) Length++;
        else Length--;
        return Length;
    }

    public long TrueMediaDuration() {
        return (WasDecoded) ? TrueMediaDuration : MediaDuration;
    }

    private void prepare() {
        try {
            extractor = new MediaExtractor();
            if (AudioPath != null) extractor.setDataSource(AudioPath);
            else extractor.setDataSource(context, uri, null);
            MediaFormat Format = extractor.getTrackFormat(0);
            PrepareEndStart(Format, true);
            extractor.selectTrack(0);//todo prepara all trakss

            if (Format.getString(MediaFormat.KEY_MIME).contains("audio")) {
                ChannelsNumber = Format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            }//todo add video

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private long lastSampleTime = 0;
    private int SampleId = 0;
    private int NewSampleSize = 0;
    private final ByteQueue byteQueue = new ByteQueue();

    private void RearrangeSamples(boolean isLastPeace) {
        while (true) {
            int byteQueueSize = byteQueue.size();
            boolean incomplete = (byteQueueSize < NewSampleSize);
            if ((incomplete && !isLastPeace) || byteQueueSize == 0) break;

            int size = (incomplete) ? byteQueueSize : NewSampleSize;

            byte[] bytes = byteQueue.peekList(size);
            BufferInfo bufferInfo = new BufferInfo();
            bufferInfo.set(0, bytes.length, SampleId * NewSampleDuration,
                    BUFFER_FLAG_KEY_FRAME);

            for (int i = 0; i < decoderListeners.size(); i++)
                decoderListeners.get(i).OnProceed(new DecoderResult(SampleId, bytes, bufferInfo));

            int bytesDuration = (bytes.length * NewSampleDuration) / NewSampleSize;

            Log.i("SampleId", SampleId + 1 + "/" + getSampleLength()
                    + " byteQueueSize: " + byteQueueSize
                    + " bytesDuration:" + bytesDuration
                    + " bytes.length:" + bytes.length);

            TrueMediaDuration += bytesDuration;
            SampleId++;
        }
    }

    private void next() {
        getInputBufferId(ID -> {
            long extractorSampleTime = extractor.getSampleTime();
            int offset = 0;
            int extractorSize = extractor.readSampleData(getInputBuffer(ID), offset);

            BufferInfo BufferInfo = new BufferInfo();
            BufferInfo.set(offset, extractorSize, extractorSampleTime, extractor.getSampleFlags());

            boolean isLastPeace = (extractorSize < 0);
            if (isLastPeace) {
                WasDecoded = true;

                RearrangeSamples(true);

                GiveBackBufferId(ID);
                for (int i = 0; i < FinishListeners.size(); i++) {
                    FinishListeners.get(i).OnDecoderEnd();
                }
            } else {
                processInput(new CodecManagerRequest(ID, BufferInfo, (codecResult) -> {
                    byte[] sample = new byte[codecResult.OutputBuffer.remaining()];
                    int sampleLength = sample.length;
                    codecResult.OutputBuffer.get(sample);
                    byteQueue.add(sample);
                    long sampleTime = codecResult.bufferInfo.presentationTimeUs;
                    long sampleDuration = sampleTime - lastSampleTime;
                    lastSampleTime = sampleTime;

                    if (NewSampleSize == 0 && sampleTime > sampleDuration * 5 && sampleLength != 0) {
                        long r = sampleLength * NewSampleDuration;
                        NewSampleSize = (int) (r / sampleDuration);
                    } else if (NewSampleSize != 0) {
                        RearrangeSamples(false);
                    }

                    extractor.advance();
                    next();
                }));
            }
        });
    }

    public void startDecoding(int NewSampleDuration) {
        this.IsStarted = true;
        this.NewSampleDuration = NewSampleDuration;
        next();
    }

    public void addOnDecodeListener(DecoderProcessListener onDecode) {
        decoderListeners.add(onDecode);
    }

    public void removeDecodeListener(DecoderProcessListener onDecode) {
        decoderListeners.remove(onDecode);
    }

    public void addOnEndListener(DecoderEndListener onEnd) {
        FinishListeners.add(onEnd);
    }

    public void removeOnEndListener(DecoderEndListener onEnd) {
        FinishListeners.remove(onEnd);
    }

}