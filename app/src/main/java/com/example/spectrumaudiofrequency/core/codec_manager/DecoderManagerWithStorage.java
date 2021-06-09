package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaFormat;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.example.spectrumaudiofrequency.core.codec_manager.dbDecoderManager.MediaSpecs;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Extended Code manager, but contains methods for obtaining samples asynchronously.
 * The samples are saved in a database.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DecoderManagerWithStorage extends DecoderManager {
    private final LinkedList<PeriodRequest> RequestsPromises = new LinkedList<>();
    private CacheQueue<Long, CodecSample> SamplesCache;
    private dbDecoderManager dbOfDecoder;
    private CountDownLatch awaitRestart = null;
    private int MaxAllocation;
    private ExecutorService SingleThreadExecutor;

    public DecoderManagerWithStorage(Context context, int ResourceId, Rearranger rearranger) {
        super(context, ResourceId, rearranger);
        PrepareDataBase();
    }

    public DecoderManagerWithStorage(Context context, String AudioPath, Rearranger rearranger) {
        super(context, AudioPath, rearranger);
        PrepareDataBase();
    }

    private void PrepareDataBase() {
        SamplesCache = new CacheQueue<>();
        dbOfDecoder = new dbDecoderManager(context, this);
        SingleThreadExecutor = Executors.newSingleThreadExecutor();
        IsDecoded = dbOfDecoder.MediaIsDecoded();

        MaxAllocation = AvailableMaxAllocationOfSamples(400);
        addOnMetricsDefinedListener(sampleMetrics ->
                MaxAllocation = AvailableMaxAllocationOfSamples(sampleMetrics.SampleSize));

        if (IsDecoded) {
            MediaSpecs mediaSpecs = dbOfDecoder.getMediaSpecs();
            this.TrueMediaDuration = mediaSpecs.TrueMediaDuration;
            this.SampleDuration = (int) mediaSpecs.SampleDuration;
            this.SampleSize = mediaSpecs.SampleSize;
        } else {
            super.addOnDecodingListener(decoderResult -> {
                //Log.i("Decoding", ((double) decoderResult.bufferInfo.presentationTimeUs / getTrueMediaDuration() * 100) + "%");
                //Log.i("freeMemory", "" + Runtime.getRuntime().freeMemory() + " MaxAllocation:" + MaxAllocation);
                KeepRequestsPromises(decoderResult);
                if (SamplesCache.size() < MaxAllocation)
                    SamplesCache.put(decoderResult.SampleId, decoderResult);
                dbOfDecoder.add(decoderResult.SampleId, decoderResult.bytes);
            });

            super.addOnDecoderFinishListener(() -> {
                dbOfDecoder.setDecoded();
                KeepRequestsPromises();
            });

            super.start();
        }
    }

    private int AvailableMaxAllocationOfSamples(int SampleSize) {
        long freeMemory = Runtime.getRuntime().freeMemory();
        long max = freeMemory / 20;
        return (int) max / SampleSize;
    }

    private synchronized void KeepRequestsPromises() {
        KeepRequestsPromises(new DecoderResult(-1, null, null));
    }

    private synchronized void KeepRequestsPromises(DecoderResult decoderResult) {
        int request = 0;
        while (request < RequestsPromises.size()) {
            PeriodRequest promise = RequestsPromises.get(request);
            if (promise.RequiredSampleId == decoderResult.SampleId) {
                RequestsPromises.remove(promise);
                promise.DecodingListener.onDecoded(decoderResult);
            } else if (promise.RequiredSampleId < decoderResult.SampleId || IsDecoded) {
                RequestsPromises.remove(promise);
                makeRequest(promise);
            } else request++;
        }
    }

    public int getNumberOfSamples() {
        if (IsDecoded) return dbOfDecoder.getNumberOfSamples();
        else return super.getNumberOfSamples();
    }

    public int getSampleDuration() {
        return this.SampleDuration;
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

            CodecSample codecSample = null;
            if (SamplesCache.size() > 0)
                codecSample = SamplesCache.pollFrist(periodRequest.RequiredSampleId);

            if (codecSample == null) {
                codecSample = dbOfDecoder.getCodecSample(periodRequest.RequiredSampleId);
            }

            if (codecSample == null) {
                if (IsDecoded) {
                    periodRequest.DecodingListener.onDecoded(
                            new DecoderResult(periodRequest.RequiredSampleId, new byte[0], (null)));
                } else RequestsPromises.add(periodRequest);
            } else {
                periodRequest.DecodingListener.onDecoded(
                        new DecoderResult(periodRequest.RequiredSampleId, codecSample));
            }
        });

    }

    public void restart() {
        if (awaitRestart != null) awaitRestart.countDown();
    }

    public void pause() {
        if (!IsDecoded) super.pause();
        else awaitRestart = new CountDownLatch(1);
    }

    @Override
    public MediaFormat getOutputFormat() {
        return super.getOutputFormat();
    }

    public void clear() {
        dbOfDecoder.clear();
    }

    public void close() {
        dbOfDecoder.close();
    }

}