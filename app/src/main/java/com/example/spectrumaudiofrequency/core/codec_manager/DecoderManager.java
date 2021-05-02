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
import com.example.spectrumaudiofrequency.core.codec_manager.MediaFormatConverter.CodecSample;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;

import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;
import static com.example.spectrumaudiofrequency.util.Files.getFileName;
import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

/**
 *
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DecoderManager extends CodecManager {
    public interface DecoderReadyListener {
        void OnReady(int SampleDuration, int SampleSize);
    }

    public interface DecoderEndListener {
        void OnDecoderEnd();
    }

    public interface DecoderProcessListener {
        void OnProceed(DecoderResult decoderResult);
    }

    public static class DecoderResult extends CodecManagerResult {
        public boolean IsLastSample;
        public int SampleId = -1;
        public byte[] Sample;
        public BufferInfo bufferInfo;

        public DecoderResult(boolean IsLastSample, int SampleId, byte[] Sample, BufferInfo bufferInfo) {
            super(null, bufferInfo);
            this.IsLastSample = IsLastSample;
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

    public static class PeriodRequest {
        int RequiredSampleId;
        DecoderProcessListener DecoderListener;

        public PeriodRequest(int RequiredSampleId, DecoderProcessListener DecoderListener) {
            this.RequiredSampleId = RequiredSampleId;
            this.DecoderListener = DecoderListener;
        }
    }

    private final ArrayList<DecoderProcessListener> DecoderListeners = new ArrayList<>();
    private final ArrayList<DecoderReadyListener> OnDecoderReadyListeners = new ArrayList<>();
    private final ArrayList<DecoderEndListener> FinishListeners = new ArrayList<>();
    private final ArrayList<CodecSample> CodecSamples = new ArrayList<>();

    private final ByteQueue byteQueue = new ByteQueue();

    private OutputListener TreatOutput;

    public int ChannelsNumber;
    private int SampleDuration;
    final Context context;
    final String MediaName;

    private Uri uri;
    private String AudioPath = null;
    private MediaExtractor extractor;

    private int SampleId = 0;
    private int NewSampleSize = 0;

    double TrueMediaDuration;
    int NewSampleDuration = 0;
    boolean DecodingFinish = false;

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

    private void executeOnDecoderReadyListeners(int sampleDuration, int sampleLength) {
        for (int i = 0; i < OnDecoderReadyListeners.size(); i++)
            OnDecoderReadyListeners.get(i).OnReady(sampleDuration, sampleLength);
    }

    private void executeOnEndListener() {
        for (int i = 0; i < FinishListeners.size(); i++) FinishListeners.get(i).OnDecoderEnd();
    }

    private void executeDecodeListeners(DecoderResult decoderResult) {
        for (int i = 0; i < DecoderListeners.size(); i++)
            DecoderListeners.get(i).OnProceed(decoderResult);
    }

    long SampleTimeExpected = 0;

    private void RearrangeSamplesSize(boolean isLastPeace) {
        while (true) {
            int byteQueueSize = byteQueue.size();
            boolean incomplete = (byteQueueSize < NewSampleSize);
            if ((incomplete && !isLastPeace) || byteQueueSize == 0 ||
                    NewSampleDuration == 0 || NewSampleSize == 0) break;

            int size = (incomplete) ? byteQueueSize : NewSampleSize;

            byte[] bytes = byteQueue.peekList(size);
            BufferInfo bufferInfo = new BufferInfo();
            bufferInfo.set(0, bytes.length, SampleId * NewSampleDuration,
                    BUFFER_FLAG_KEY_FRAME);

            double bytesDuration = ((bytes.length * NewSampleDuration) / (double) NewSampleSize);

            boolean TrueLastSample = (bytesDuration <= NewSampleDuration && isLastPeace);

            executeDecodeListeners(new DecoderResult(TrueLastSample, SampleId, bytes, bufferInfo));

            int sampleLength = getSampleLength();
            Log.i("RearrangeSamplesSize", "Sample " + (SampleId + 1) + "/"
                    + ((NewSampleDuration > 0) ? getSampleLength() : "?")
                    + " percentage " + new DecimalFormat("0.00")
                    .format((double) SampleId / sampleLength * 100) + '%'
                    + " byteQueueSize: " + byteQueueSize
                    + " bytesDuration:" + bytesDuration
                    + " bytes.length:" + bytes.length
                    + " codecSamplesSize:" + CodecSamples.size());


            TrueMediaDuration += bytesDuration;
            SampleId++;
        }
        if (isLastPeace) executeOnEndListener();
    }


    private void OrderSamples(CodecManagerResult codecResult) {
        boolean IsLastSample = codecResult == null;
        if (!IsLastSample) {
            //Log.i("On OrderSamples", "codecSamplesSize:" + codecSamples.size() + " " + codecResult.toString());

            byte[] sample = new byte[codecResult.OutputBuffer.remaining()];
            codecResult.OutputBuffer.get(sample);

            if (codecResult.bufferInfo.presentationTimeUs == SampleTimeExpected) {
                byteQueue.add(sample);
                RearrangeSamplesSize(IsLastSample);
                SampleTimeExpected += SampleDuration;
            } else CodecSamples.add(new CodecSample(codecResult.bufferInfo, sample));
        }

        if (CodecSamples.size() == 0 && IsLastSample) {
            RearrangeSamplesSize(IsLastSample);
        } else {
            int sampleID = 0;
            while (sampleID < CodecSamples.size()) {
                CodecSample codecSample = CodecSamples.get(sampleID);
                if (codecSample.bufferInfo.presentationTimeUs == SampleTimeExpected) {
                    byteQueue.add(codecSample.bytes);
                    RearrangeSamplesSize(IsLastSample);
                    SampleTimeExpected += SampleDuration;
                    CodecSamples.remove(sampleID);
                    sampleID = 0;
                } else sampleID++;
            }
        }

        if (!IsLastSample) next();
    }

    long lastSampleTime = 0;

    private void awaitSampleDuration(CodecManagerResult codecResult) {
        byte[] sample = new byte[codecResult.OutputBuffer.remaining()];
        codecResult.OutputBuffer.get(sample);
        long sampleTime = codecResult.bufferInfo.presentationTimeUs;
        SampleDuration = (int) (sampleTime - lastSampleTime);
        lastSampleTime = sampleTime;

        if ((NewSampleSize == 0 || NewSampleDuration == 0)
                && SampleDuration != 0
                && sample.length > 0
                && sampleTime > SampleDuration * 4) {
            executeOnDecoderReadyListeners(SampleDuration, sample.length);
            TreatOutput = this::OrderSamples;
        }
        CodecSamples.add(new CodecSample(codecResult.bufferInfo, sample));
        next();
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
        }//todo add video

        TreatOutput = this::awaitSampleDuration;
    }

    private void putData(int InputID) {
        if (DecodingFinish) return;

        long extractorSampleTime = extractor.getSampleTime();
        int offset = 0;
        int extractorSize = extractor.readSampleData(getInputBuffer(InputID), offset);

        //  Log.i("On putData", "extractorSampleTime:" + extractorSampleTime);

        if (extractorSampleTime > -1) {
            BufferInfo BufferInfo = new BufferInfo();
            BufferInfo.set(offset, extractorSize, extractorSampleTime, extractor.getSampleFlags());
            addOnOutputPromise(new OutputPromise(extractorSampleTime, TreatOutput));
            processInput(new CodecManagerRequest(InputID, BufferInfo));
            extractor.advance();
        } else {
            OrderSamples(null);
            GiveBackInputID(InputID);
        }
    }

    private void next() {
        getInputBufferId(this::putData);
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
        addOnDecodeReadyListener((SampleDuration, SampleSize) -> {
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
        });
        for (int i = 0; i < 2; i++) next();
    }

    public void addOnDecodeListener(DecoderProcessListener onDecode) {
        DecoderListeners.add(onDecode);
    }

    public void removeDecodeListener(DecoderProcessListener onDecode) {
        DecoderListeners.remove(onDecode);
    }

    public void addOnDecodeReadyListener(DecoderReadyListener onReady) {
        OnDecoderReadyListeners.add(onReady);
    }

    public void removeDecodeReadyListener(DecoderReadyListener onReady) {
        OnDecoderReadyListeners.remove(onReady);
    }

    public void addOnEndListener(DecoderEndListener onFinish) {
        FinishListeners.add(onFinish);
    }

    public void removeOnEndListener(DecoderEndListener onEnd) {
        FinishListeners.remove(onEnd);
    }

}