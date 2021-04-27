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

        public short[][] getSampleChannels(DecoderManager decoderManagerWithCacheManager) {
            short[] shorts = new short[Sample.length / 2];
            ByteBuffer.wrap(Sample).order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts);

            short[][] SamplesChannels;
            SamplesChannels = new short[decoderManagerWithCacheManager.ChannelsNumber]
                    [shorts.length / decoderManagerWithCacheManager.ChannelsNumber];
            for (int i = 0; i < SamplesChannels.length; ++i) {
                for (int j = 0; j < SamplesChannels[i].length; j++) {
                    SamplesChannels[i][j] = shorts[j * decoderManagerWithCacheManager.ChannelsNumber + i];
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

    public int ChannelsNumber;

    private final ArrayList<DecoderProcessListener> decoderListeners = new ArrayList<>();
    private final ArrayList<DecoderReadyListener> decoderReadyListeners = new ArrayList<>();
    private final ArrayList<DecoderEndListener> FinishListeners = new ArrayList<>();
    private final ByteQueue byteQueue = new ByteQueue();

    final Context context;
    final String MediaName;

    private Uri uri;
    private String AudioPath = null;
    private MediaExtractor extractor;

    private long lastSampleTime = 0;
    private int SampleId = 0;
    private int NewSampleSize = 0;

    double TrueMediaDuration;
    double NewSampleDuration = 0;
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

    private void RearrangeSamples(boolean isLastPeace) {
        while (true) {
            int byteQueueSize = byteQueue.size();
            boolean incomplete = (byteQueueSize < NewSampleSize);
            if ((incomplete && !isLastPeace) || byteQueueSize == 0 ||
                    NewSampleDuration == 0 || NewSampleSize == 0)
                break;

            int size = (incomplete) ? byteQueueSize : NewSampleSize;

            byte[] bytes = byteQueue.peekList(size);
            BufferInfo bufferInfo = new BufferInfo();
            bufferInfo.set(0, bytes.length, (long) (SampleId * NewSampleDuration),
                    BUFFER_FLAG_KEY_FRAME);

            double bytesDuration = ((bytes.length * NewSampleDuration) / NewSampleSize);

            boolean TrueLastSample = (bytesDuration <= NewSampleDuration && isLastPeace);
            for (int i = 0; i < decoderListeners.size(); i++)
                decoderListeners.get(i).OnProceed(new DecoderResult(TrueLastSample,
                        SampleId, bytes, bufferInfo));

            int sampleLength = getSampleLength();
            Log.i("onRearrange", "Sample " + (SampleId + 1) + "/"
                    + ((NewSampleDuration > 0) ? getSampleLength() : "?")
                    + " percentage " + new DecimalFormat("0.00")
                    .format((double) SampleId / sampleLength * 100) + '%'
                    + " byteQueueSize: " + byteQueueSize
                    + " bytesDuration:" + bytesDuration
                    + " bytes.length:" + bytes.length);

            TrueMediaDuration += bytesDuration;
            SampleId++;
        }
        if (isLastPeace) for (int i = 0; i < FinishListeners.size(); i++)
            FinishListeners.get(i).OnDecoderEnd();
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
                DecodingFinish = true;
                RearrangeSamples(true);
                Log.i("Peace", "" + SampleId + " TrueDuration" + TrueMediaDuration() + " NewSampleDuration" + NewSampleDuration + " NewSize :" + NewSampleSize);
                GiveBackBufferId(ID);

            } else {
                addOnOutputListenerConsumable((codecResult) -> {
                    byte[] sample = new byte[codecResult.OutputBuffer.remaining()];
                    int sampleLength = sample.length;
                    codecResult.OutputBuffer.get(sample);
                    byteQueue.add(sample);
                    long sampleTime = codecResult.bufferInfo.presentationTimeUs;
                    int sampleDuration = (int) (sampleTime - lastSampleTime);
                    Log.i("sampleDuration", "" + sampleDuration + " sampleSize: " + sample.length);
                    lastSampleTime = sampleTime;

                    if ((NewSampleSize == 0 || NewSampleDuration == 0) && sampleDuration != 0
                            && sampleTime > sampleDuration * 4 && sampleLength != 0) {
                        for (int i = 0; i < decoderReadyListeners.size(); i++)
                            decoderReadyListeners.get(i).OnReady(sampleDuration, sampleLength);
                    } else if (NewSampleSize != 0 || NewSampleDuration != 0) {
                        RearrangeSamples(false);
                    }

                    extractor.advance();
                    next();
                });
                putInput(new CodecManagerRequest(ID, BufferInfo));
            }
        });
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
                NewSampleDuration = (r / (double) SampleSize);
            } else {
                try {
                    throw new Exception("NewSampleSize and NewSampleDuration not defined");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        next();
    }

    public void addOnDecodeListener(DecoderProcessListener onDecode) {
        decoderListeners.add(onDecode);
    }

    public void removeDecodeListener(DecoderProcessListener onDecode) {
        decoderListeners.remove(onDecode);
    }

    public void addOnDecodeReadyListener(DecoderReadyListener onReady) {
        decoderReadyListeners.add(onReady);
    }

    public void removeDecodeReadyListener(DecoderReadyListener onReady) {
        decoderReadyListeners.remove(onReady);
    }

    public void addOnEndListener(DecoderEndListener onFinish) {
        FinishListeners.add(onFinish);
    }

    public void removeOnEndListener(DecoderEndListener onEnd) {
        FinishListeners.remove(onEnd);
    }

}