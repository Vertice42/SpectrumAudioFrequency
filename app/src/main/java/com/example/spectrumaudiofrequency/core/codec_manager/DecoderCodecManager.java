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
import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager.CodecManagerRequest;
import com.example.spectrumaudiofrequency.core.codec_manager.media_decoder.dbDecoderManager;
import com.example.spectrumaudiofrequency.core.codec_manager.media_decoder.dbDecoderManager.MediaSpecs;

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
public class DecoderCodecManager {
    public interface DecoderEndListener {
        void OnDecoderEnd();
    }

    public interface DecoderProcessListener {
        void OnProceed(DecoderResult decoderResult);
    }

    public static class DecoderResult extends CodecManager.CodecManagerResult {
        public byte[] Sample;
        public BufferInfo bufferInfo;

        public DecoderResult(byte[] sample, BufferInfo bufferInfo) {
            super(null, bufferInfo);

            Sample = sample;
            this.bufferInfo = bufferInfo;
        }

        public DecoderResult() {
            super(null, null);
        }

        public boolean SampleTimeNotExist() {
            return (bufferInfo == null);
        }

        public short[][] getSampleChannels(DecoderCodecManager decoderCodecManager) {
            short[] shorts = new short[Sample.length / 2];
            ByteBuffer.wrap(Sample).order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts);

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
                    "BsChannels=" + Arrays.toString(Sample) +
                    ", presentationTimeUs=" + bufferInfo.presentationTimeUs +
                    '}';
        }
    }

    public static class PeriodRequest {
        long RequiredPeace;
        DecoderProcessListener DecoderListener;

        public PeriodRequest(long RequiredPeace, DecoderProcessListener DecoderListener) {
            this.RequiredPeace = RequiredPeace;
            this.DecoderListener = DecoderListener;
        }
    }

    private DecoderEndListener onEndListener;
    public int NewSampleDuration = 24000;
    private boolean IsDecoded = false;

    private dbDecoderManager dbOfDecoder;
    private final ArrayList<PeriodRequest> RequestsPromises = new ArrayList<>();

    public CodecManager codecManager;//todo tornar extend
    public int ChannelsNumber;

    private final String MediaName;
    private MediaExtractor extractor;

    private Context context;
    private Uri uri;
    private String AudioPath = null;

    public int getSampleLength() {
        int Length = (int) Math.ceil(TrueMediaDuration() / (double) NewSampleDuration);
        if (IsDecoded) Length++;
        else Length--;
        return Length;
    }

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

    private long TrueMediaDuration = -1;

    public long TrueMediaDuration() {
        return (IsDecoded) ? TrueMediaDuration : codecManager.MediaDuration;
    }

    private void prepare() {
        onEndListener = () -> {
            Log.i("ON finish", "empty");
        };

        try {
            extractor = new MediaExtractor();
            if (AudioPath != null) extractor.setDataSource(AudioPath);
            else extractor.setDataSource(context, uri, null);
            MediaFormat Format = extractor.getTrackFormat(0);

            Log.i("MediaFormat", Format.toString());

            extractor.selectTrack(0);//todo prepara all trakss

            if (Format.getString(MediaFormat.KEY_MIME).contains("audio")) {
                ChannelsNumber = Format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            }//todo add video

            this.dbOfDecoder = new dbDecoderManager(context, MediaName);
            codecManager = new CodecManager(Format, true);

            IsDecoded = dbOfDecoder.MediaIsDecoded(MediaName);

            if (IsDecoded) {
                MediaSpecs mediaSpecs = dbOfDecoder.getMediaSpecs();
                TrueMediaDuration = mediaSpecs.TrueMediaDuration;
                NewSampleDuration = mediaSpecs.SampleDuration;
            } else {
                startDecoding();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void KeepPromises(int Peacetime, BufferInfo bufferInfo, byte[] sample) {
        int size = RequestsPromises.size();
        int requestIndex = 0;
        for (int i = 0; i < size; i++) {
            PeriodRequest request = RequestsPromises.get(requestIndex);

            if (request.RequiredPeace == Peacetime) {
                RequestsPromises.remove(request);
                request.DecoderListener.OnProceed(new DecoderResult(sample, bufferInfo));
            } else if (request.RequiredPeace < Peacetime || IsDecoded) {
                RequestsPromises.remove(request);
                addRequest(request);
            } else {
                requestIndex++;
            }
        }
    }

    private long lastSampleTime = 0;
    private int SamplePeace = 0;
    private int NewSampleSize = 0;
    private final ByteQueue byteQueue = new ByteQueue();

    private void RearrangeSamples(boolean isLastPeace) {
        while (true) {
            int byteQueueSize = byteQueue.size();
            boolean incomplete = (byteQueueSize < NewSampleSize);
            if ((incomplete && !isLastPeace) || byteQueueSize == 0) break;

            int size = (incomplete) ? byteQueueSize : NewSampleSize;

            byte[] bytes = byteQueue.peekList(size);
            dbOfDecoder.addSamplePiece(SamplePeace, bytes);

            BufferInfo bufferInfo = new BufferInfo();
            bufferInfo.set(0, bytes.length, SamplePeace * NewSampleDuration,
                    BUFFER_FLAG_KEY_FRAME);
            KeepPromises(SamplePeace, bufferInfo, bytes);
            int bytesDuration = (bytes.length * NewSampleDuration) / NewSampleSize;

            Log.i("SamplePeace", SamplePeace + 1 + "/" + getSampleLength()
                    + " byteQueueSize: " + byteQueueSize
                    + " bytesDuration:" + bytesDuration
                    + " bytes.length:" + bytes.length);
            TrueMediaDuration += bytesDuration;
            SamplePeace++;
        }
    }

    private void next() {
        codecManager.getInputBufferId(ID -> {
            long extractorSampleTime = extractor.getSampleTime();
            int offset = 0;
            int extractorSize = extractor.readSampleData(codecManager.getInputBuffer(ID), offset);

            BufferInfo BufferInfo = new BufferInfo();
            BufferInfo.set(offset, extractorSize, extractorSampleTime, extractor.getSampleFlags());

            boolean isLastPeace = (extractorSize < 0);
            if (isLastPeace) {
                IsDecoded = true;
                RearrangeSamples(isLastPeace);
                dbOfDecoder.setDecoded(
                        new MediaSpecs(MediaName, TrueMediaDuration, NewSampleDuration));
                Log.i("TRUEMediaDuration", ""
                        + TrueMediaDuration
                        + " byteQueue s: "
                        + byteQueue.size());
                codecManager.GiveBackBufferId(ID);
                KeepPromises(-1, null, null);
                onEndListener.OnDecoderEnd();
            } else {
                codecManager.processInput(new CodecManagerRequest(ID, BufferInfo, (codecResult) -> {
                    byte[] sample = new byte[codecResult.OutputBuffer.remaining()];
                    int sampleLength = sample.length;
                    codecResult.OutputBuffer.get(sample);
                    byteQueue.add(sample);
                    long sampleTime = codecResult.bufferInfo.presentationTimeUs;
                    long sampleDuration = sampleTime - lastSampleTime;
                    lastSampleTime = sampleTime;

                    if (NewSampleSize == 0 && sampleTime > sampleDuration * 5 && sampleLength != 0) {
                        long r = sampleLength * NewSampleDuration;
                        Log.i("sampleDuration???", "" + sampleDuration + " sampleLength" + sampleLength);
                        NewSampleSize = (int) (r / sampleDuration);
                    } else if (NewSampleSize != 0) {
                        RearrangeSamples(isLastPeace);
                    }

                    extractor.advance();
                    next();
                }));
            }
        });
    }

    private void startDecoding() {
        next();
    }

    private void setOnEnd(DecoderEndListener onEnd) {
        this.onEndListener = onEnd;
    }

    public void addRequest(PeriodRequest periodRequest) {
        byte[] dbSampleBytes = dbOfDecoder.getSamplePiece(periodRequest.RequiredPeace);
        if (dbSampleBytes != null) {
            BufferInfo bufferInfo = new BufferInfo();
            bufferInfo.set(0, dbSampleBytes.length,
                    periodRequest.RequiredPeace * NewSampleDuration,
                    BUFFER_FLAG_KEY_FRAME);
            periodRequest.DecoderListener.OnProceed(new DecoderResult(dbSampleBytes, bufferInfo));
        } else if (IsDecoded) {
            periodRequest.DecoderListener.OnProceed(new DecoderResult());
        } else RequestsPromises.add(periodRequest);
    }

    public void clear() {
        dbOfDecoder.deleteMediaDecoded(MediaName);
    }

    public void destroy() {
        dbOfDecoder.close();
    }

}