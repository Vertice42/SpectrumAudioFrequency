package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.example.spectrumaudiofrequency.core.ByteQueue;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;
import static com.example.spectrumaudiofrequency.util.Files.getFileName;
import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DecoderManager extends CodecManager {
    public final Context context;
    public final String MediaName;
    private final ArrayList<CodecFinishListener> OnDecoderFinishListeners = new ArrayList<>();
    private final ArrayList<OnDecodedListener> OnDecoderListeners = new ArrayList<>();
    private final ByteQueue byteQueue = new ByteQueue();
    public int ChannelsNumber;
    protected boolean DecodingFinish = false;
    protected int NewSampleDuration;
    protected double TrueMediaDuration;
    private ResultPromiseListener OnOrderlyPromiseKeep;
    private Uri uri;
    private String AudioPath = null;
    private MediaExtractor extractor;
    private int SampleId;
    private int NewSampleSize;

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

    private void executeOnFinishListener() {
        for (int i = 0; i < OnDecoderFinishListeners.size(); i++)
            OnDecoderFinishListeners.get(i).OnFinish();
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

    private void rearrangeSamplesSize(CodecSample codecSample) {
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

            /*
            Log.i("RearrangeSamplesSize", "Sample " + (SampleId + 1) + "/"
                    + ((NewSampleDuration > 0) ? getSampleLength() : "?")
                    + " percentage " + new DecimalFormat("0.00")
                    .format((double) SampleId / sampleLength * 100) + '%'
                    + " byteQueueSize: " + byteQueueSize
                    + " bytesDuration:" + bytesDuration
                    + " bytes.length:" + bytes.length);*/


            TrueMediaDuration += bytesDuration;
            SampleId++;
        }
        if (isLastPeace) executeOnFinishListener();
    }

    private void executeDecodeListeners(DecoderResult decoderResult) {
        for (int i = 0; i < OnDecoderListeners.size(); i++)
            OnDecoderListeners.get(i).OnProceed(decoderResult);
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

        OnOrderlyPromiseKeep = codecSample -> byteQueue.add(codecSample.bytes);

        addOnReadyListener((SampleDuration, SampleSize) -> {
            calculateResizing(SampleDuration, SampleSize);
            OnOrderlyPromiseKeep = this::rearrangeSamplesSize;
        });
        addOnFinishListener(() -> DecodingFinish = true);
    }

    private void calculateResizing(int SampleDuration, int SampleSize) {
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
        ForkJoinPool pool;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) pool = ForkJoinPool.commonPool();
        else pool = new ForkJoinPool();
        while (RemainingBuffers() > 0) pool.execute(this::onSampleSorted);
    }

    private void putData(int InputID) {
        long extractorSampleTime = extractor.getSampleTime();
        int offset = 0;
        int extractorSize = extractor.readSampleData(getInputBuffer(InputID), offset);

        if (extractorSampleTime > -1) {
            BufferInfo BufferInfo = new BufferInfo();
            BufferInfo.set(offset, extractorSize, extractorSampleTime, extractor.getSampleFlags());
            addOrderlyOutputPromise(new OutputPromise(extractorSampleTime, OnOrderlyPromiseKeep));
            processInput(new CodecManagerRequest(InputID, BufferInfo));
            extractor.advance();
        } else {
            stop();
            GiveBackInputID(InputID);
        }
    }

    @Override
    public void onSampleSorted() {
        addInputIdRequest(this::putData);
    }

    public void addOnFinishListener(CodecFinishListener onFinish) {
        OnDecoderFinishListeners.add(onFinish);
    }

    public void addOnDecodeListener(OnDecodedListener onDecodedListener) {
        OnDecoderListeners.add(onDecodedListener);
    }

    public void removeOnDecodeListener(OnDecodedListener onDecodedListener) {
        OnDecoderListeners.remove(onDecodedListener);
    }

    public int getDecodeListenersListSize() {
        return OnDecoderListeners.size();
    }

    public void removeOnFinishListener(CodecFinishListener codecFinishListener) {
        OnDecoderFinishListeners.remove(codecFinishListener);
    }

    public interface OnDecodedListener {
        void OnProceed(DecoderResult decoderResult);
    }

    public static class PeriodRequest {
        int RequiredSampleId;
        OnDecodedListener DecoderListener;

        public PeriodRequest(int RequiredSampleId, OnDecodedListener DecoderListener) {
            this.RequiredSampleId = RequiredSampleId;
            this.DecoderListener = DecoderListener;
        }
    }

    public static class DecoderResult extends CodecSample {
        public int SampleId;
        public BufferInfo bufferInfo;

        public DecoderResult(int SampleId, byte[] Sample, BufferInfo bufferInfo) {
            super(bufferInfo, Sample);
            this.SampleId = SampleId;
            this.bufferInfo = bufferInfo;
        }

        public boolean SampleTimeNotExist() {
            return (bufferInfo == null);
        }

        public short[][] getSampleChannels(DecoderManager decoderManager) {
            short[] shorts = new short[bytes.length / 2];
            ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts);

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
                    "BsChannels=" + Arrays.toString(bytes) +
                    ", presentationTimeUs=" + bufferInfo.presentationTimeUs +
                    '}';
        }
    }

}