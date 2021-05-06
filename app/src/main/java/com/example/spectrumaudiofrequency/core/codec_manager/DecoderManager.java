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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;
import static com.example.spectrumaudiofrequency.util.Files.getFileName;
import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

/**
 *
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DecoderManager extends CodecManager {

    public interface DecoderProcessListener {
        void OnProceed(DecoderResult decoderResult);
    }

    private final ArrayList<CodecFinishListener> FinishListeners = new ArrayList<>();

    public static class PeriodRequest {
        int RequiredSampleId;
        DecoderProcessListener DecoderListener;

        public PeriodRequest(int RequiredSampleId, DecoderProcessListener DecoderListener) {
            this.RequiredSampleId = RequiredSampleId;
            this.DecoderListener = DecoderListener;
        }
    }

    private final ArrayList<DecoderProcessListener> DecoderListeners = new ArrayList<>();
    protected boolean DecodingFinish = false;

    private final ByteQueue byteQueue = new ByteQueue();
    protected int NewSampleDuration = 0;

    public int ChannelsNumber;
    final Context context;
    final String MediaName;

    private Uri uri;
    private String AudioPath = null;
    private MediaExtractor extractor;
    protected double TrueMediaDuration;
    private int SampleId = 0;
    PromiseResultListener onKeep;
    private int NewSampleSize = 0;

    private void executeOnFinishListener() {
        for (int i = 0; i < FinishListeners.size(); i++) FinishListeners.get(i).OnFinish();
    }

    public DecoderManager(Context context, int ResourceId) {
        super();
        this.context = context;
        this.uri = getUriFromResourceId(context, ResourceId);
        this.MediaName = getFileName(context.getResources().getResourceName(ResourceId));
        prepare();
    }

    public DecoderManager(Context context, String AudioPath) {
        super();
        this.context = context;
        this.AudioPath = AudioPath;
        this.MediaName = getFileName(AudioPath);
        prepare();
    }

    public boolean IsStarted() {
        return (NewSampleDuration > 0);
    }

    public int getSampleLength() {
        int Length = (int) Math.ceil(TrueMediaDuration() / (double) NewSampleDuration);
        if (DecodingFinish) Length++;
        else Length--;
        return Length;
    }

    public long TrueMediaDuration() {
        return (DecodingFinish) ? (long) TrueMediaDuration : MediaDuration;
    }

    private void RearrangeSamplesSize(CodecSample codecSample) {
        boolean isLastPeace = codecSample.bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM;
        byteQueue.add(codecSample.bytes);

        while (true) {
            int byteQueueSize = byteQueue.size();
            boolean incomplete = (byteQueueSize < NewSampleSize);
            if ((incomplete && !isLastPeace) || byteQueueSize == 0 ||
                    NewSampleDuration == 0 || NewSampleSize == 0) break;

            int size = (incomplete) ? byteQueueSize : NewSampleSize;

            byte[] bytes = byteQueue.peekList(size);
            BufferInfo bufferInfo = new BufferInfo();

            double bytesDuration = ((bytes.length * NewSampleDuration) / (double) NewSampleSize);

            boolean TrueLastSample = (size < NewSampleSize && isLastPeace);

            int flag = (TrueLastSample) ? BUFFER_FLAG_END_OF_STREAM : BUFFER_FLAG_KEY_FRAME;
            bufferInfo.set(0,
                    bytes.length,
                    SampleId * NewSampleDuration,
                    flag);

            executeDecodeListeners(new DecoderResult(SampleId, bytes, bufferInfo));

            int sampleLength = getSampleLength();
            Log.i("RearrangeSamplesSize", "Sample " + (SampleId + 1) + "/"
                    + ((NewSampleDuration > 0) ? getSampleLength() : "?")
                    + " percentage " + new DecimalFormat("0.00")
                    .format((double) SampleId / sampleLength * 100) + '%'
                    + " byteQueueSize: " + byteQueueSize
                    + " bytesDuration:" + bytesDuration
                    + " bytes.length:" + bytes.length);


            TrueMediaDuration += bytesDuration;
            SampleId++;
        }
        if (isLastPeace) executeOnFinishListener();
    }

    private void executeDecodeListeners(DecoderResult decoderResult) {
        for (int i = 0; i < DecoderListeners.size(); i++)
            DecoderListeners.get(i).OnProceed(decoderResult);
    }

    private void prepare() {
        extractor = new MediaExtractor();
        try {
            if (AudioPath != null) extractor.setDataSource(AudioPath);
            else extractor.setDataSource(context, uri, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat Format = extractor.getTrackFormat(0);
        PrepareEndStart(Format, true);
        extractor.selectTrack(0);//todo prepara all trakss

        if (Format.getString(MediaFormat.KEY_MIME).contains("audio")) {
            ChannelsNumber = Format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        }//todo add

        onKeep = codecSample -> byteQueue.add(codecSample.bytes);

        addDecoderReadyListener((SampleDuration, SampleSize) -> {
            CalculateResizing(SampleDuration, SampleSize);
            onKeep = this::RearrangeSamplesSize;
        });
        addOnFinishListener(() -> DecodingFinish = true);
    }

    private void CalculateResizing(int SampleDuration, int SampleSize) {
        if (NewSampleDuration > 0) {
            double r = SampleSize * NewSampleDuration;
            NewSampleSize = (int) Math.ceil(r / SampleDuration);
        } else if (NewSampleSize > 0) {
            long r = NewSampleSize * SampleDuration;
            NewSampleDuration = (int) Math.abs(r / (double) SampleSize);
        } else {
            try {
                throw new Exception("NewSampleSize and NewSampleDuration not defined");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setNewSampleDuration(int NewSampleDuration) {
        this.NewSampleDuration = NewSampleDuration;
    }

    public void setNewSampleSize(int NewSampleSize) {
        this.NewSampleSize = NewSampleSize;
    }

    /**
     * Decoding starts, the sample size must be defined.
     * The sample time must be greater than 0.
     */
    public void startDecoding() {
        while (RemainingBuffers() > 0) forkJoinPool.execute(this::NextPeace);
    }

    private void putData(int InputID) {
        long extractorSampleTime = extractor.getSampleTime();
        int offset = 0;
        int extractorSize = extractor.readSampleData(getInputBuffer(InputID), offset);

        if (extractorSampleTime > -1) {
            BufferInfo BufferInfo = new BufferInfo();
            BufferInfo.set(offset, extractorSize, extractorSampleTime, extractor.getSampleFlags());
            addOrderlyOutputPromise(new OutputOrderPromise(extractorSampleTime, onKeep));
            processInput(new CodecManagerRequest(InputID, BufferInfo));
            extractor.advance();
        } else {
            stop();
            GiveBackInputID(InputID);
        }
    }

    @Override
    public void NextPeace() {
        getInputBufferID(this::putData);
    }

    public void addOnFinishListener(CodecFinishListener onFinish) {
        FinishListeners.add(onFinish);
    }

    public void addOnDecodeListener(DecoderProcessListener onDecode) {
        DecoderListeners.add(onDecode);
    }

    public void removeDecodeListener(DecoderProcessListener onDecode) {
        DecoderListeners.remove(onDecode);
    }

    public void removeOnFinishListener(CodecFinishListener onFinish) {
        FinishListeners.remove(onFinish);
    }

    public static class DecoderResult extends CodecManagerResult {
        public int SampleId = -1;
        public byte[] Sample;
        public BufferInfo bufferInfo;

        public DecoderResult(int SampleId, byte[] Sample, BufferInfo bufferInfo) {
            super(null, bufferInfo);
            this.SampleId = SampleId;
            this.Sample = Sample;
            this.bufferInfo = bufferInfo;
        }

        public DecoderResult() {
            super(null, null);
        }

        public boolean SampleTimeNotExist() {
            return (bufferInfo == null);
        }

        public short[][] getSampleChannels(DecoderManager decoderManager) {
            short[] shorts = new short[Sample.length / 2];
            ByteBuffer.wrap(Sample).order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts);

            short[][] SamplesChannels;
            SamplesChannels = new short[decoderManager.ChannelsNumber]
                    [shorts.length / decoderManager.ChannelsNumber];
            for (int i = 0; i < SamplesChannels.length; ++i) {
                for (int j = 0; j < SamplesChannels[i].length; j++) {
                    SamplesChannels[i][j] = shorts[j * decoderManager.ChannelsNumber + i];
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

}