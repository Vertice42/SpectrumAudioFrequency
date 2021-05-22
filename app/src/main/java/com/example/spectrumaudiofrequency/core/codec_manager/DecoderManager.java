package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.example.spectrumaudiofrequency.BuildConfig;
import com.example.spectrumaudiofrequency.core.ByteQueue;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;
import static com.example.spectrumaudiofrequency.util.Files.getFileName;
import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DecoderManager extends CodecManager {
    public final Context context;
    public final String MediaName;
    private final ArrayList<CodecFinishListener> OnDecoderFinishListeners = new ArrayList<>();
    private final ArrayList<DecodingListener> OnDecoderListeners = new ArrayList<>();
    private final ByteQueue byteQueue = new ByteQueue();//todo realocasão pode gerar erros já que as amostras na verdade são shorts , com o numeo de canais > 2 > 5 refatorar
    public int ChannelsNumber;
    public boolean IsDecoded = false;
    protected int NewSampleDuration;
    protected double TrueMediaDuration;
    int NewSampleSize;
    private ResultPromiseListener OnKeepSortedSamplePromise;
    private Uri uri;
    private String AudioPath = null;
    private MediaExtractor extractor;
    private int SampleId;

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

    public int getNumberOfSamples() {
        if (BuildConfig.DEBUG && NewSampleDuration <= 0) {
            throw new AssertionError("Assertion failed");
        }
        int Length = (int) Math.ceil(getTrueMediaDuration() / (double) NewSampleDuration);
        if (IsDecoded) Length++;
        else Length--;
        return Length;
    }

    public long getTrueMediaDuration() {
        return (IsDecoded) ? (long) TrueMediaDuration : MediaDuration;
    }

    private void rearrangeSampleSize(CodecSample codecSample) {
        boolean isLastPeace = codecSample.bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM;

        Log.i("rearrangeSampleSize", " bytes.length" + codecSample.bytes.length);
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

            boolean TrueLastSample = (byteQueue.size() == 0 && isLastPeace);

            int flag = (TrueLastSample) ? BUFFER_FLAG_END_OF_STREAM : BUFFER_FLAG_KEY_FRAME;
            bufferInfo.set(0,
                    bytes.length,
                    SampleId * NewSampleDuration,
                    flag);

            executeDecodeListeners(new DecoderResult(SampleId, bytes, bufferInfo));
            if (TrueLastSample) {
                Log.i("TrueLastSample", "rearrangeSampleSize: ");
                executeOnFinishListener();
            }
            /*
            int sampleLength = getNumberOfSamples();
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
    }

    private void executeDecodeListeners(DecoderResult decoderResult) {
        for (int i = 0; i < OnDecoderListeners.size(); i++) {
            Log.i("TAG", "executeDecodeListeners: " + executorService.isShutdown() + executorService.toString());
            DecodingListener decodingListener = OnDecoderListeners.get(i);
            executorService.submit(() -> decodingListener.onDecoded(decoderResult));
        }
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

        OnKeepSortedSamplePromise = codecSample -> byteQueue.add(codecSample.bytes);

        addOnReadyListener((SampleDuration, SampleSize) -> {
            calculateReallocation(SampleDuration, SampleSize);
            OnKeepSortedSamplePromise = this::rearrangeSampleSize;
        });
        addFinishListener(() -> IsDecoded = true);
    }

    private void calculateReallocation(int SampleDuration, int SampleSize) {
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

    public int getNewSampleSize() {
        return NewSampleSize;
    }

    /**
     * Decoding starts, the sample size must be defined.
     * The sample time must be greater than 0.
     */

    private void putData(int InputID) {
        if (!IsDecoded) {
            long extractorSampleTime = extractor.getSampleTime();
            int offset = 0;
            int extractorSize = extractor.readSampleData(getInputBuffer(InputID), offset);

            if (extractorSampleTime > -1) {
                BufferInfo BufferInfo = new BufferInfo();
                BufferInfo.set(offset, extractorSize, extractorSampleTime, extractor.getSampleFlags());
                addOrderlyOutputPromise(new OutputPromise(extractorSampleTime, OnKeepSortedSamplePromise));
                processInput(new CodecManagerRequest(InputID, BufferInfo));
                extractor.advance();
            } else {
                stop();
                GiveBackInputID(InputID);
            }
        } else {
            GiveBackInputID(InputID);
        }
    }

    void addPutDataRequests() {
        int i = 0;
        int interations = 1;
        interations += getNumberOfInputsIdsAvailable();
        do {
            addInputIdRequest(this::putData);
            i++;
        } while (i < interations);
    }

    public void startDecoding() {
        addPutDataRequests();
        this.onInputReliased = InputID -> addPutDataRequests();
    }

    public void addFinishListener(CodecFinishListener onFinish) {
        OnDecoderFinishListeners.add(onFinish);
    }

    public void addDecodingListener(DecodingListener decodingListener) {
        OnDecoderListeners.add(decodingListener);
    }

    public void removeOnDecodeListener(DecodingListener decodingListener) {
        OnDecoderListeners.remove(decodingListener);
    }

    public int getDecodeListenersListSize() {
        return OnDecoderListeners.size();
    }

    public void removeOnFinishListener(CodecFinishListener codecFinishListener) {
        OnDecoderFinishListeners.remove(codecFinishListener);
    }

    public interface DecodingListener {
        void onDecoded(DecoderResult decoderResult);
    }

    public static class PeriodRequest {
        int RequiredSampleId;
        DecoderManager.DecodingListener DecodingListener;

        public PeriodRequest(int RequiredSampleId, DecoderManager.DecodingListener DecodingListener) {
            this.RequiredSampleId = RequiredSampleId;
            this.DecodingListener = DecodingListener;
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


        public static short[][] separateSampleChannels(byte[] sampleData, int ChannelsNumber) {
            short[] shorts = new short[sampleData.length / 2];
            ByteBuffer.wrap(sampleData).order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts);

            short[][] SamplesChannels;
            SamplesChannels = new short[ChannelsNumber]
                    [shorts.length / ChannelsNumber];
            for (int i = 0; i < SamplesChannels.length; ++i) {
                for (int j = 0; j < SamplesChannels[i].length; j++) {
                    SamplesChannels[i][j] = shorts[j * ChannelsNumber + i];
                }
            }

            if (SamplesChannels[0].length < 1) SamplesChannels = new short[2][200];
            return SamplesChannels;
        }

        public static byte[] SampleChannelsToBytes(short[][] sampleData, int ChannelsNumber) {
            ByteBuffer byteBuffer = ByteBuffer.allocate((sampleData[0].length * ChannelsNumber) * 2);
            byteBuffer.order(ByteOrder.nativeOrder());

            for (int i = 0; i < sampleData[1].length; i++) {
                for (int j = 0; j < ChannelsNumber; j++) {
                    byteBuffer.putShort(sampleData[j][i]);
                }
            }
            byteBuffer.flip();

            byte[] result = new byte[byteBuffer.limit()];
            byteBuffer.get(result);
            return result;


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