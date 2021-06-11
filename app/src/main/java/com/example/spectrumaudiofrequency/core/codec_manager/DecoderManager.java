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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.spectrumaudiofrequency.util.Files.getFileName;
import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DecoderManager extends CodecManager {
    public final Context context;
    public final String MediaName;
    private final LinkedList<Runnable> OnDecodingFinishListeners = new LinkedList<>();
    private final LinkedList<DecodingListener> onDecodingListeners = new LinkedList<>();
    private final LinkedList<SampleMetricsListener> onMetricsDefinedListeners = new LinkedList<>();
    private final SampleRearranger sampleRearranger;
    private final ByteQueue byteQueue = new ByteQueue(1024 * 5000);
    public int ChannelsNumber;
    protected double TrueMediaDuration;
    protected boolean IsDecoded = false;
    private ResultPromiseListener keepSortedSamplePromise;
    private final Runnable next = this::addRequestsOfPutData;
    private MediaExtractor extractor;
    private Uri uri;
    private String AudioPath;
    private int SamplesDecoded = 0;

    public DecoderManager(Context context, int ResourceId, SampleRearranger sampleRearranger) {
        super();
        this.context = context;
        this.uri = getUriFromResourceId(context, ResourceId);
        this.MediaName = getFileName(context.getResources().getResourceName(ResourceId));
        this.sampleRearranger = sampleRearranger;
        prepare();
    }

    public DecoderManager(Context context, String AudioPath, SampleRearranger sampleRearranger) {
        super();
        this.context = context;
        this.AudioPath = AudioPath;
        this.MediaName = getFileName(AudioPath);
        this.sampleRearranger = sampleRearranger;
        prepare();
    }

    public boolean IsDecoded() {
        return IsDecoded;
    }

    public double getTrueMediaDuration() {
        return (IsDecoded) ? TrueMediaDuration : MediaDuration;
    }

    public int getNumberOfSamples() {
        if (!IsReady()) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            addOnMetricsDefinedListener(sampleMetrics -> countDownLatch.countDown());

            AtomicReference<DecodingListener> reference = new AtomicReference<>();
            reference.set(addOnDecodingListener(decoderResult -> {
                countDownLatch.countDown();
                removeOnDecodingListener(reference.get());
            }));

            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return (int) Math.ceil(getTrueMediaDuration() / SampleDuration);
    }

    private void rearrangeSampleSize(CodecSample codecSample) {
        byteQueue.put(codecSample.bytes);
        while (true) {
            int byteQueueSize = byteQueue.getSize();
            boolean incomplete = (byteQueueSize < SampleSize);
            if ((incomplete && !codecSample.isLastPeace) || byteQueueSize == 0) break;

            int sample_size = (incomplete) ? byteQueueSize : SampleSize;
            byte[] bytes = byteQueue.pollList(sample_size);

            double bytesDuration = ((bytes.length * SampleDuration) / (double) SampleSize);
            boolean TrueLastSample = (byteQueue.getSize() == 0 && codecSample.isLastPeace);

            BufferInfo bufferInfo = new BufferInfo();
            bufferInfo.set(0, bytes.length,
                    SamplesDecoded * SampleDuration,
                    0);

            TrueMediaDuration += bytesDuration;
            executeDecodeListeners(new DecoderResult(SamplesDecoded, bytes, bufferInfo));
            if (TrueLastSample) executeOnFinishListener();
            SamplesDecoded++;
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
        prepareEndStart(Format, true);
        extractor.selectTrack(0);//todo prepara all trakss

        if (Format.getString(MediaFormat.KEY_MIME).contains("audio")) {
            ChannelsNumber = Format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        }

        keepSortedSamplePromise = codecSample -> byteQueue.put(codecSample.bytes);
        //todo realocasão pode gerar erros já que as amostras na verdade são shorts , com o numeo de canais > 2 > 5 refatorar

        addOnReadyListener(metrics -> {
            SampleMetrics sampleMetrics = sampleRearranger.rearrange(metrics);
            this.SampleDuration = sampleMetrics.SampleDuration;
            this.SampleSize = sampleMetrics.SampleSize;

            executeOnMetricsDefinedListener(sampleMetrics);
            keepSortedSamplePromise = this::rearrangeSampleSize;
        });
    }

    private synchronized void putData(int inputBufferId) {
        if (!IsStopped) {
            long extractorSampleTime = extractor.getSampleTime();
            int offset = 0;
            int extractorSize = extractor.readSampleData(getInputBuffer(inputBufferId), offset);
            if (extractorSize == -1) {
                stop();
                giveBackInputID(inputBufferId);
            } else {
                BufferInfo BufferInfo = new BufferInfo();
                BufferInfo.set(offset,
                        extractorSize,
                        extractorSampleTime,
                        extractor.getSampleFlags());
                processInput(inputBufferId, BufferInfo, keepSortedSamplePromise);
                extractor.advance();
            }
        } else {
            giveBackInputID(inputBufferId);
        }
    }

    void addRequestsOfPutData() {
        int interactions = 1;
        interactions += getNumberOfInputsIdsAvailable();
        for (int i = 0; i < interactions; i++) addInputIdRequest(this::putData);
    }

    public void start() {
        next.run();
        addOnInputIdAvailableListener(next);
    }

    public void pause() {
        removeOnInputIdAvailableListener(next);
    }

    public void stop() {
        removeOnInputIdAvailableListener(next);
        super.stop();
    }

    public void addOnDecoderFinishListener(Runnable onFinish) {
        if (IsDecoded) onFinish.run();
        else OnDecodingFinishListeners.add(onFinish);
    }

    public DecodingListener addOnDecodingListener(DecodingListener decodingListener) {
        onDecodingListeners.add(decodingListener);
        return decodingListener;
    }

    public void addOnMetricsDefinedListener(SampleMetricsListener sampleMetricsListener) {
        onMetricsDefinedListeners.add(sampleMetricsListener);
    }

    private void executeOnMetricsDefinedListener(SampleMetrics sampleMetrics) {
        for (int i = 0; i < onMetricsDefinedListeners.size(); i++) {
            onMetricsDefinedListeners.get(i).OnAvailable(sampleMetrics);
        }
    }

    private void executeDecodeListeners(DecoderResult decoderResult) {
        for (int i = 0; i < onDecodingListeners.size(); i++) {
            onDecodingListeners.get(i).onDecoded(decoderResult);
        }
    }

    private void executeOnFinishListener() {
        IsDecoded = true;
        for (int i = 0; i < OnDecodingFinishListeners.size(); i++) {
            OnDecodingFinishListeners.get(i).run();
        }
    }

    public void removeOnDecodingListener(DecodingListener decodingListener) {
        onDecodingListeners.remove(decodingListener);
    }

    public int getDecodeListenersListSize() {
        return onDecodingListeners.size();
    }

    public void removeOnFinishListener(Runnable codecFinishListener) {
        OnDecodingFinishListeners.remove(codecFinishListener);
    }

    public interface SampleRearranger {
        SampleMetrics rearrange(SampleMetrics sampleMetrics);
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
        public long SampleId;

        public DecoderResult(int SampleId, byte[] sample, BufferInfo bufferInfo) {
            super(bufferInfo, sample);
            this.SampleId = SampleId;
            this.bufferInfo = bufferInfo;
        }

        public DecoderResult(int SampleId, CodecSample codecSample) {
            super(codecSample.bufferInfo, codecSample.bytes);
            this.SampleId = SampleId;
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