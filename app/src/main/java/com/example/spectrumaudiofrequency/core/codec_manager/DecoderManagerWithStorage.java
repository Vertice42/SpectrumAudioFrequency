package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaFormat;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.example.spectrumaudiofrequency.core.codec_manager.media_decoder.dbDecoderManager;
import com.example.spectrumaudiofrequency.core.codec_manager.media_decoder.dbDecoderManager.MediaSpecs;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Extended Code manager, but contains methods for obtaining samples asynchronously.
 * The samples are saved in a database.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DecoderManagerWithStorage extends DecoderManager {
    private final ArrayList<PeriodRequest> RequestsPromises = new ArrayList<>();
    private CacheQueue<Long, CodecSample> SamplesCache;
    private dbDecoderManager dbOfDecoder;
    private ExecutorService dbExecutor;
    private CountDownLatch awaitRestart = null;

    public DecoderManagerWithStorage(Context context, int ResourceId) {
        super(context, ResourceId);
        PrepareDataBase();
    }

    public DecoderManagerWithStorage(Context context, String AudioPath) {
        super(context, AudioPath);
        PrepareDataBase();
    }

    private void KeepPromises(DecoderResult decoderResult) {
        int i = 0;
        while (i < RequestsPromises.size()) {
            PeriodRequest request = RequestsPromises.get(i);
            if (request.RequiredSampleId == decoderResult.SampleId) {
                RequestsPromises.remove(request);
                request.DecodingListener.onDecoded(decoderResult);
            } else if (request.RequiredSampleId < decoderResult.SampleId || IsDecoded) {
                RequestsPromises.remove(request);
                addRequest(request);
            } else i++;
        }
    }

    private void PrepareDataBase() {
        SamplesCache = new CacheQueue<>();
        dbExecutor = Executors.newSingleThreadExecutor();

        dbOfDecoder = new dbDecoderManager(context, MediaName);
        IsDecoded = dbOfDecoder.MediaIsDecoded(MediaName);
        if (IsDecoded) {
            MediaSpecs mediaSpecs = dbOfDecoder.getMediaSpecs();
            this.TrueMediaDuration = mediaSpecs.TrueMediaDuration;
            this.NewSampleDuration = (int) mediaSpecs.SampleDuration;
            this.NewSampleSize = mediaSpecs.SampleSize;
        }

        addDecodingListener(decoderResult -> {
            dbExecutor.execute(() -> {
                dbOfDecoder.add(decoderResult.SampleId, decoderResult.bytes);
                if (SamplesCache.size() < 2)
                    SamplesCache.put(decoderResult.SampleId, decoderResult);
            });

            KeepPromises(decoderResult);
        });

        addFinishListener(() -> {
            dbOfDecoder.setDecoded(new MediaSpecs(MediaName,
                    getTrueMediaDuration(),
                    NewSampleDuration,
                    getNewSampleSize()));
            KeepPromises(new DecoderResult(getNumberOfSamples(), null, null));
        });
    }

    public int getNumberOfSamples() {
        if (IsDecoded) return dbOfDecoder.getNumberOfSamples();
        else return super.getNumberOfSamples();
    }

    public int getSampleDuration() {
        return this.NewSampleDuration;
    }

    public void addRequest(PeriodRequest periodRequest) {
        dbExecutor.execute(() -> {
            if (awaitRestart != null) {
                try {
                    awaitRestart.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            CodecSample codecSample = null;
            if (SamplesCache.size() > 0)
                codecSample = SamplesCache.get(periodRequest.RequiredSampleId);
            if (codecSample == null) {
                codecSample = dbOfDecoder.getCodecSample
                        (periodRequest.RequiredSampleId, NewSampleDuration);
            }
            if (codecSample == null) {
                if (IsDecoded) {
                    periodRequest.DecodingListener.onDecoded(new DecoderResult
                            (periodRequest.RequiredSampleId, new byte[0], (null)));
                } else RequestsPromises.add(periodRequest);
            } else {
                DecoderResult decoderResult;
                decoderResult = new DecoderResult(periodRequest.RequiredSampleId, codecSample);
                periodRequest.DecodingListener.onDecoded(decoderResult);
            }
        });
    }

    public void start() {
        if (!IsDecoded) super.start();
        else if (awaitRestart != null) awaitRestart.countDown();
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
        dbOfDecoder.deleteMediaDecoded(MediaName);
    }

    public void destroy() {
        dbOfDecoder.close();
    }

}