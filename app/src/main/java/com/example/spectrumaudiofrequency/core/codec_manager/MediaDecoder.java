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
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

public class MediaDecoder extends CodecManager {
    public final String MediaName;
    private final LinkedList<Runnable> OnDecodingFinishListeners = new LinkedList<>();
    private final LinkedList<DecodingListener> onDecodingListeners = new LinkedList<>();
    private final LinkedList<MetricsDefinedListener> onMetricsDefinedListeners = new LinkedList<>();
    private final MediaExtractor mediaExtractor;
    private final int TrackIndex;
    public int ChannelsNumber;
    protected double TrueMediaDuration;
    protected boolean IsCompletelyCodified = false;
    private SampleRearranger sampleRearranger;
    private ResultPromiseListener keepSortedSamplePromise;
    private boolean IsStopped;

    public MediaDecoder(Context context, int ResourceId, int TrackIndex) {
        Uri uri = getUriFromResourceId(context, ResourceId);
        this.mediaExtractor = new MediaExtractor();
        this.MediaName = context.getResources().getResourceEntryName(ResourceId);
        this.TrackIndex = TrackIndex;
        prepare(context, uri);
    }

    public MediaDecoder(Context context, Uri AudioUri, String MediaName, int TrackIndex) {
        this.mediaExtractor = new MediaExtractor();
        this.MediaName = MediaName;
        this.TrackIndex = TrackIndex;
        prepare(context, AudioUri);
    }

    public static short[][] converterBytesToChannels(byte[] sampleData, int ChannelsNumber) {
        assert sampleData.length > 0;
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

    public static byte[] converterChannelsToBytes(short[][] sampleData) {
        int channelsNumber = sampleData.length;
        ByteBuffer byteBuffer = ByteBuffer.allocate((sampleData[0].length * channelsNumber) * 2);
        byteBuffer.order(ByteOrder.nativeOrder());

        for (int i = 0; i < sampleData[1].length; i++) {
            for (short[] sampleDatum : sampleData) {
                byteBuffer.putShort(sampleDatum[i]);
            }
        }
        byteBuffer.flip();

        byte[] result = new byte[byteBuffer.limit()];
        byteBuffer.get(result);
        return result;
    }

    private void putNextSample() {
        int interactions = 1;
        interactions += getInputsIdsAvailableSize();
        for (int i = 0; i < interactions; i++) addInputIdRequest(this::putData);
    }

    private void prepare(Context context, Uri uri) {
        try {
            mediaExtractor.setDataSource(context, uri, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaExtractor.selectTrack(TrackIndex);
        MediaFormat mediaFormat = mediaExtractor.getTrackFormat(TrackIndex);

        if (mediaFormat.getString(MediaFormat.KEY_MIME).contains("audio")) {
            ChannelsNumber = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        }

        LinkedList<CodecSample> codecSamplesAwait = new LinkedList<>();

        keepSortedSamplePromise = codecSamplesAwait::add;

        addOnReadyListener((SamplesHaveEqualSize, metrics) -> {
            AtomicInteger SamplesDecoded = new AtomicInteger();

            if (!SamplesHaveEqualSize) setSampleRearranger(sampleMetrics -> sampleMetrics);

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
                keepSortedSamplePromise = codecSample -> deliverySample(codecSample,
                        SamplesDecoded,
                        byteDuration);
            }
        });

        super.prepare(mediaFormat, true);
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

    private synchronized void rearrangeAndDeliverySample(CodecSample codecSample,
                                                         AtomicInteger SamplesDecoded,
                                                         double byteDuration,
                                                         ByteQueue byteQueue) {
        byteQueue.put(codecSample.bytes);
        while (true) {
            int byteQueueSize = byteQueue.getSize();
            boolean incomplete = (byteQueueSize < SampleSize);
            if ((incomplete && !codecSample.isLastPeace()) || byteQueueSize == 0) break;

            int sample_size = (incomplete) ? byteQueueSize : SampleSize;
            byte[] bytes = byteQueue.pollList(sample_size);
            double bytesDuration = bytes.length * byteDuration;
            boolean TrueLastSample = (byteQueue.getSize() == 0 && codecSample.isLastPeace());
            BufferInfo bufferInfo = new BufferInfo();
            bufferInfo.set(0, bytes.length,
                    (long) TrueMediaDuration,
                    (TrueLastSample) ? BUFFER_FLAG_END_OF_STREAM : 0);
            executeDecodeListeners(new DecoderResult(SamplesDecoded.get(), bytes, bufferInfo));
            if (TrueLastSample) executeOnDecoderFinishListener();

            TrueMediaDuration += bytesDuration;
            SamplesDecoded.incrementAndGet();
        }
    }

    private synchronized void deliverySample(CodecSample codecSample,
                                             AtomicInteger SamplesDecoded,
                                             double byteDuration) {
        BufferInfo bufferInfo = new BufferInfo();
        bufferInfo.set(codecSample.bufferInfo.offset,
                codecSample.bufferInfo.size,
                (long) TrueMediaDuration,
                codecSample.bufferInfo.flags);

        executeDecodeListeners(new DecoderResult(SamplesDecoded.get(),
                codecSample.bytes,
                bufferInfo));
        if (codecSample.isLastPeace()) executeOnDecoderFinishListener();
        TrueMediaDuration += codecSample.bytes.length * byteDuration;
        SamplesDecoded.incrementAndGet();
    }

    private synchronized void putData(int inputBufferId) {
        if (!IsClosed) {
            long extractorSampleTime = mediaExtractor.getSampleTime();
            int offset = 0;
            int extractorSize = mediaExtractor.readSampleData
                    (getInputBuffer(inputBufferId), offset);
            if (extractorSize == -1) {
                close();
                this.addOnFinishListener(this::executeOnDecoderFinishListener);
                this.putSignalOfEndOfStream(inputBufferId);
            } else {
                BufferInfo BufferInfo = new BufferInfo();
                BufferInfo.set(offset,
                        extractorSize,
                        extractorSampleTime,
                        mediaExtractor.getSampleFlags());
                this.processInput(inputBufferId, BufferInfo,
                        codecSample -> keepSortedSamplePromise.onKeep(codecSample));
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

    public void close() {
        removeOnInputIdAvailableListener(this::putNextSample);
        super.close();
    }

    public void addOnDecoderFinishListener(Runnable onFinish) {
        if (IsCompletelyCodified) onFinish.run();
        else OnDecodingFinishListeners.add(onFinish);
    }

    public void addOnDecodingListener(DecodingListener decodingListener) {
        onDecodingListeners.add(decodingListener);
    }

    public void addOnMetricsDefinedListener(MetricsDefinedListener metricsDefinedListener) {
        if (IsReady()) {
            metricsDefinedListener
                    .onMetricsDefinedListener(new SampleMetrics(SampleDuration, SampleSize));
        } else {
            onMetricsDefinedListeners.add(metricsDefinedListener);
        }
    }

    private void executeOnMetricsDefinedListener(SampleMetrics sampleMetrics) {
        for (int i = 0; i < onMetricsDefinedListeners.size(); i++) {
            onMetricsDefinedListeners.get(i).onMetricsDefinedListener(sampleMetrics);
        }
    }

    private synchronized void executeDecodeListeners(DecoderResult decoderResult) {
        for (int i = 0; i < onDecodingListeners.size(); i++) {
            onDecodingListeners.get(i).onDecoded(decoderResult);
        }
    }

    private void executeOnDecoderFinishListener() {
        IsCompletelyCodified = true;
        for (int i = 0; i < OnDecodingFinishListeners.size(); i++) {
            OnDecodingFinishListeners.get(i).run();
        }
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

    public interface MetricsDefinedListener {
        void onMetricsDefinedListener(SampleMetrics sampleMetrics);
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

        public short[][] converterBytesToChannels(int ChannelsNumber) {
            return MediaDecoder.converterBytesToChannels(this.bytes, ChannelsNumber);
        }

        @Override
        public @NotNull String toString() {
            return "DecoderResult{" +
                    ", presentationTimeUs = " + bufferInfo.presentationTimeUs +
                    ", flags = " + bufferInfo.flags +
                    '}';
        }
    }

}