package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.util.Log;

import com.example.spectrumaudiofrequency.core.codec_manager.dbDecoderManager.MediaSpecs;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Extended Code manager, but contains methods for obtaining samples asynchronously.
 * The samples are saved in a database.
 */
public class MediaDecoderWithStorage extends MediaDecoder {
    private final LinkedList<PeriodRequest> RequestsPromises = new LinkedList<>();
    private HashMap<Integer, CodecSample> SamplesCache;
    private dbDecoderManager dbOfDecoder;
    private CountDownLatch awaitRestart = null;
    private int MaxAllocationOfSamples;
    private ExecutorService SingleThreadExecutor;

    public MediaDecoderWithStorage(Context context, int ResourceId) {
        super(context, ResourceId);
        PrepareDataBase(context);
    }

    public MediaDecoderWithStorage(Context context, String AudioPath) {
        super(AudioPath);
        PrepareDataBase(context);
    }

    private void PrepareDataBase(Context context) {
        dbOfDecoder = new dbDecoderManager(context, this);

        SamplesCache = new HashMap<>();
        SingleThreadExecutor = Executors.newSingleThreadExecutor();
        IsCompletelyCodified = dbOfDecoder.MediaIsDecoded();

        MaxAllocationOfSamples = availableMaxAllocationOfSamples(400);

        if (IsCompletelyCodified) {
            MediaSpecs mediaSpecs = dbOfDecoder.getMediaSpecs();
            this.TrueMediaDuration = (double) mediaSpecs.TrueMediaDuration;
            this.SampleDuration = (int) mediaSpecs.SampleDuration;
            this.SampleSize = mediaSpecs.SampleSize;

        } else {
            addOnMetricsDefinedListener(sampleMetrics ->
                    MaxAllocationOfSamples = availableMaxAllocationOfSamples
                            (sampleMetrics.SampleSize));

            super.addOnDecodingListener(decoderResult -> {
                Log.i("TAG", "Decoding: " + ((double) decoderResult.bufferInfo.presentationTimeUs / getTrueMediaDuration() * 100) + "%");
                //Log.i("freeMemory", "" + Runtime.getRuntime().freeMemory() + " MaxAllocation:" + MaxAllocation);
                if (SamplesCache.size() < MaxAllocationOfSamples)
                    SamplesCache.put(decoderResult.SampleId, decoderResult);
                dbOfDecoder.add(decoderResult.SampleId, decoderResult.bytes);
                KeepRequestsPromises(decoderResult);
            });

            super.addOnDecoderFinishListener(() -> {
                dbOfDecoder.setDecoded();
                KeepRequestsPromises();
            });
        }
    }

    private int availableMaxAllocationOfSamples(int SampleSize) {
        long freeMemory = Runtime.getRuntime().freeMemory();
        long max = freeMemory / 20;
        return (int) max / SampleSize;
    }

    private synchronized void KeepRequestsPromises() {
        KeepRequestsPromises(new DecoderResult(-1, null, null));
    }

    private synchronized void KeepRequestsPromises(DecoderResult decoderResult) {
        while (RequestsPromises.size() > 0) {
            PeriodRequest periodRequest = RequestsPromises.get(0);
            if (periodRequest.RequiredSampleId <= decoderResult.SampleId || IsCompletelyCodified) {
                RequestsPromises.remove(periodRequest);
                deliveryRequest(periodRequest);
            } else break;
        }
    }

    private synchronized void addRequestsPromises(PeriodRequest periodRequest) {
        RequestsPromises.add(periodRequest);
    }

    public SampleMetrics getSampleMetrics() {
        AtomicReference<SampleMetrics> sampleMetrics = new AtomicReference<>();

        if (IsCompletelyCodified) {
            sampleMetrics.set(new SampleMetrics(this.SampleDuration, this.SampleSize));
        } else {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            addOnMetricsDefinedListener(sampleMetricsDefined -> {
                sampleMetrics.set(sampleMetricsDefined);
                countDownLatch.countDown();
            });
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return sampleMetrics.get();
    }

    public int getSamplesNumber() {
        if (IsCompletelyCodified) return dbOfDecoder.getNumberOfSamples();
        else return super.getSamplesNumber();
    }

    public int getSampleDuration() {
        return this.SampleDuration;
    }

    private synchronized void deliveryRequest(PeriodRequest periodRequest) {
        if (awaitRestart != null) {
            try {
                awaitRestart.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        int requiredSampleId = periodRequest.RequiredSampleId;
        CodecSample codecSample = SamplesCache.get(requiredSampleId);
        if (codecSample != null) {
            SamplesCache.remove(requiredSampleId);
        } else {
            codecSample = dbOfDecoder.getCodecSample(requiredSampleId);
        }

        if (IsCompletelyCodified && codecSample == null) {
            codecSample = new DecoderResult(requiredSampleId, new byte[0], (null));
        }

        assert codecSample != null;

        long presentationTimeUs = -1;
        if (codecSample.bufferInfo != null)
            presentationTimeUs = codecSample.bufferInfo.presentationTimeUs;
        periodRequest.DecodingListener.onDecoded(new DecoderResult
                (requiredSampleId, codecSample));
    }

    public void makeRequest(PeriodRequest periodRequest) {
        if (IsCompletelyCodified && RequestsPromises.size() == 0) {
            SingleThreadExecutor.execute(() -> deliveryRequest(periodRequest));
        } else addRequestsPromises(periodRequest);
    }

    public void start() {
        super.start();
    }

    public void restart() {
        if (!IsCompletelyCodified) super.start();
        if (awaitRestart != null) awaitRestart.countDown();
    }

    public void pause() {
        if (!IsCompletelyCodified) super.pause();
        else awaitRestart = new CountDownLatch(1);
    }

    public void clear() {
        dbOfDecoder.clear();
    }

    public void close() {
        dbOfDecoder.close();
    }

    public interface PeriodRequestListener {
        void onDecoded(DecoderResult decoderResult);
    }

    public static class PeriodRequest {
        int RequiredSampleId;
        PeriodRequestListener DecodingListener;

        public PeriodRequest(int RequiredSampleId, PeriodRequestListener DecodingListener) {
            this.RequiredSampleId = RequiredSampleId;
            this.DecodingListener = DecodingListener;
        }
    }
}