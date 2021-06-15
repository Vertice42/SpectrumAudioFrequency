package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import com.example.spectrumaudiofrequency.core.ByteQueue;
import com.example.spectrumaudiofrequency.core.ByteQueueDynamic;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.spectrumaudiofrequency.util.Files.getFileName;
import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

public class DecoderManager extends CodecManager {
    public final String MediaName;
    private final LinkedList<Runnable> OnDecodingFinishListeners = new LinkedList<>();
    private final LinkedList<DecodingListener> onDecodingListeners = new LinkedList<>();
    private final LinkedList<SampleMetricsListener> onMetricsDefinedListeners = new LinkedList<>();
    private final MediaExtractor mediaExtractor = new MediaExtractor();
    public int ChannelsNumber;
    protected double TrueMediaDuration;
    protected boolean IsCompletelyCodified = false;
    private SampleRearranger sampleRearranger;
    private ResultPromiseListener keepSortedSamplePromise;

    public DecoderManager(Context context, int ResourceId) {
        Uri uri = getUriFromResourceId(context, ResourceId);
        this.MediaName = getFileName(context.getResources().getResourceName(ResourceId));
        prepare(context, uri);
    }

    public DecoderManager(String AudioPath) {
        this.MediaName = getFileName(AudioPath);
        prepare(AudioPath);
    }

    private void putNextSample() {
        int interactions = 1;
        interactions += getNumberOfInputsIdsAvailable();
        for (int i = 0; i < interactions; i++) addInputIdRequest(this::putData);
    }

    private void prepare(String AudioPath) {
        try {
            mediaExtractor.setDataSource(AudioPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        prepare();
    }

    private void prepare(Context context, Uri uri) {
        try {
            mediaExtractor.setDataSource(context, uri, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        prepare();
    }

    private void prepare() {
        MediaFormat Format = mediaExtractor.getTrackFormat(0);
        mediaExtractor.selectTrack(0);//todo prepara all trakss

        if (Format.getString(MediaFormat.KEY_MIME).contains("audio")) {
            ChannelsNumber = Format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        }

        LinkedList<CodecSample> codecSamplesAwait = new LinkedList<>();

        keepSortedSamplePromise = codecSamplesAwait::add;

        addOnReadyListener(metrics -> {
            AtomicInteger SamplesDecoded = new AtomicInteger();

            if (sampleRearranger != null) {
                SampleMetrics newMetrics = sampleRearranger.rearrange(metrics);
                if (newMetrics.SampleSize % (2 * ChannelsNumber) != 0)
                    throw new AssertionError(" New Metrics not is possible");
                metrics = newMetrics;

                this.SampleDuration = metrics.SampleDuration;
                this.SampleSize = metrics.SampleSize;

                executeOnMetricsDefinedListener(metrics);
                ByteQueueDynamic byteQueue = new ByteQueueDynamic(SampleSize * 4);

                double byteDuration = SampleDuration / (double) SampleSize;

                while (codecSamplesAwait.size() > 0) {
                    CodecSample codecSample = codecSamplesAwait.pollFirst();
                    assert codecSample != null;
                    rearrangeAndDeliverySample(codecSample,
                            SamplesDecoded,
                            byteDuration,
                            byteQueue);
                }

                keepSortedSamplePromise = codecSample -> rearrangeAndDeliverySample(codecSample,
                        SamplesDecoded,
                        byteDuration,
                        byteQueue);
            } else {
                this.SampleDuration = metrics.SampleDuration;
                this.SampleSize = metrics.SampleSize;

                double byteDuration = SampleDuration / (double) SampleSize;
                while (codecSamplesAwait.size() > 0) {
                    CodecSample codecSample = codecSamplesAwait.pollFirst();
                    assert codecSample != null;
                    deliverySample(codecSample, SamplesDecoded, byteDuration);
                }

                executeOnMetricsDefinedListener(metrics);
                keepSortedSamplePromise = codecSample ->
                        deliverySample(codecSample, SamplesDecoded, byteDuration);
            }
        });

        super.prepare(Format, true);
    }

    public boolean IsDecoded() {
        return IsCompletelyCodified;
    }

    public double getTrueMediaDuration() {
        return (IsCompletelyCodified) ? TrueMediaDuration : MediaDuration;
    }

    public int getNumberOfSamples() {
        if (!IsReady()) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            addOnMetricsDefinedListener(sampleMetrics -> countDownLatch.countDown());
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return (int) Math.ceil(getTrueMediaDuration() / SampleDuration);
    }

    private void rearrangeAndDeliverySample(CodecSample codecSample,
                                            AtomicInteger SamplesDecoded,
                                            double byteDuration,
                                            ByteQueue byteQueue) {
        byteQueue.put(codecSample.bytes);
        while (true) {
            int byteQueueSize = byteQueue.getSize();
            boolean incomplete = (byteQueueSize < SampleSize);
            if ((incomplete && !codecSample.isLastPeace) || byteQueueSize == 0) break;

            int sample_size = (incomplete) ? byteQueueSize : SampleSize;
            byte[] bytes = byteQueue.pollList(sample_size);

            boolean TrueLastSample = (byteQueue.getSize() == 0 && codecSample.isLastPeace);

            TrueMediaDuration += bytes.length * byteDuration;

            BufferInfo bufferInfo = new BufferInfo();
            bufferInfo.set(0, bytes.length,
                    (long) TrueMediaDuration,
                    0);

            executeDecodeListeners(new DecoderResult(SamplesDecoded.get(), bytes, bufferInfo));
            if (TrueLastSample) executeOnFinishListener();
            SamplesDecoded.incrementAndGet();
        }
    }

    private void deliverySample(CodecSample codecSample,
                                AtomicInteger SamplesDecoded,
                                double byteDuration) {
        double bytesDuration = codecSample.bytes.length * byteDuration;
        BufferInfo bufferInfo = new BufferInfo();
        bufferInfo.set(codecSample.bufferInfo.offset, codecSample.bufferInfo.size,
                (long) TrueMediaDuration,
                codecSample.bufferInfo.flags);
        executeDecodeListeners(new DecoderResult(SamplesDecoded.get(),
                codecSample.bytes,
                bufferInfo));
        TrueMediaDuration += bytesDuration;
        if (codecSample.isLastPeace) executeOnFinishListener();
        SamplesDecoded.incrementAndGet();
    }

    private synchronized void putData(int inputBufferId) {
        if (!IsStopped) {
            long extractorSampleTime = mediaExtractor.getSampleTime();
            int offset = 0;
            int extractorSize = mediaExtractor.readSampleData(getInputBuffer(inputBufferId), offset);
            if (extractorSize == -1) {
                stop();
                giveBackInputID(inputBufferId);
            } else {
                BufferInfo BufferInfo = new BufferInfo();
                BufferInfo.set(offset,
                        extractorSize,
                        extractorSampleTime,
                        mediaExtractor.getSampleFlags());
                processInput(inputBufferId, BufferInfo, keepSortedSamplePromise);
                mediaExtractor.advance();
            }
        } else {
            giveBackInputID(inputBufferId);
        }
    }

    public void setSampleRearranger(SampleRearranger sampleRearranger) {
        this.sampleRearranger = sampleRearranger;
    }

    public void start() {
        putNextSample();
        addOnInputIdAvailableListener(this::putNextSample);
    }

    public void pause() {
        removeOnInputIdAvailableListener(this::putNextSample);
    }

    public void stop() {
        removeOnInputIdAvailableListener(this::putNextSample);
        super.stop();
    }

    public void addOnDecoderFinishListener(Runnable onFinish) {
        if (IsCompletelyCodified) onFinish.run();
        else OnDecodingFinishListeners.add(onFinish);
    }

    public DecodingListener addOnDecodingListener(DecodingListener decodingListener) {
        onDecodingListeners.add(decodingListener);
        return decodingListener;
    }

    public void addOnMetricsDefinedListener(SampleMetricsListener sampleMetricsListener) {
        if (IsReady()) {
            sampleMetricsListener.OnAvailable(new SampleMetrics(SampleDuration, SampleSize));
        } else {
            onMetricsDefinedListeners.add(sampleMetricsListener);
        }
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
        IsCompletelyCodified = true;
        for (int i = 0; i < OnDecodingFinishListeners.size(); i++) {
            OnDecodingFinishListeners.get(i).run();
        }
    }

    public void removeOnDecodingListener(DecodingListener decodingListener) {
        onDecodingListeners.remove(decodingListener);
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
        public int SampleId;

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