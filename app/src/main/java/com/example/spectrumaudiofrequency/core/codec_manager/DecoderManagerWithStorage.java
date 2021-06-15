package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;

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
public class DecoderManagerWithStorage extends DecoderManager {
    private final LinkedList<PeriodRequest> RequestsPromises = new LinkedList<>();
    private HashMap<Integer, CodecSample> SamplesCache;
    private dbDecoderManager dbOfDecoder;
    private CountDownLatch awaitRestart = null;
    private int MaxAllocationOfSamples;
    private ExecutorService SingleThreadExecutor;

    public DecoderManagerWithStorage(Context context, int ResourceId) {
        super(context, ResourceId);
        PrepareDataBase(context);
    }

    public DecoderManagerWithStorage(Context context, String AudioPath) {
        super(AudioPath);
        PrepareDataBase(context);
    }

    private void PrepareDataBase(Context context) {
        SamplesCache = new HashMap<>();
        dbOfDecoder = new dbDecoderManager(context, this);
        SingleThreadExecutor = Executors.newSingleThreadExecutor();
        IsCompletelyCodified = dbOfDecoder.MediaIsDecoded();

        MaxAllocationOfSamples = availableMaxAllocationOfSamples(400);

        if (IsCompletelyCodified) {
            MediaSpecs mediaSpecs = dbOfDecoder.getMediaSpecs();
            this.TrueMediaDuration = mediaSpecs.TrueMediaDuration;
            this.SampleDuration = (int) mediaSpecs.SampleDuration;
            this.SampleSize = mediaSpecs.SampleSize;
        }
        addOnMetricsDefinedListener(sampleMetrics ->
                MaxAllocationOfSamples = availableMaxAllocationOfSamples(sampleMetrics.SampleSize));

        super.addOnDecodingListener(decoderResult -> {
            //Log.i("Decoding", ((double) decoderResult.bufferInfo.presentationTimeUs / getTrueMediaDuration() * 100) + "%");
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

    private int availableMaxAllocationOfSamples(int SampleSize) {
        long freeMemory = Runtime.getRuntime().freeMemory();
        long max = freeMemory / 20;
        return (int) max / SampleSize;
    }

    private synchronized void KeepRequestsPromises() {
        KeepRequestsPromises(new DecoderResult(-1, null, null));
    }

    private synchronized void KeepRequestsPromises(DecoderResult decoderResult) {
        while (RequestsPromises.size() != 0) {
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

    public int getNumberOfSamples() {
        if (IsCompletelyCodified) return dbOfDecoder.getNumberOfSamples();
        else return super.getNumberOfSamples();
    }

    public int getSampleDuration() {
        return this.SampleDuration;
    }

    private synchronized void deliveryRequest(PeriodRequest periodRequest) {
        CodecSample codecSample = SamplesCache.get(periodRequest.RequiredSampleId);
        if (codecSample != null) {
            SamplesCache.remove(periodRequest.RequiredSampleId);
        } else {
            codecSample = dbOfDecoder.getCodecSample(periodRequest.RequiredSampleId);
        }

        if (IsCompletelyCodified && codecSample == null) {
            codecSample = new DecoderResult(periodRequest.RequiredSampleId, new byte[0], (null));
        }

        assert codecSample != null;
        periodRequest.DecodingListener.onDecoded(new DecoderResult
                (periodRequest.RequiredSampleId, codecSample));
    }

    public void makeRequest(PeriodRequest periodRequest) {
        SingleThreadExecutor.execute(() -> {
            if (awaitRestart != null) {
                try {
                    awaitRestart.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (IsCompletelyCodified && RequestsPromises.size() == 0)
                deliveryRequest(periodRequest);
            else addRequestsPromises(periodRequest);
        });

    }

    public void start() {
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

}