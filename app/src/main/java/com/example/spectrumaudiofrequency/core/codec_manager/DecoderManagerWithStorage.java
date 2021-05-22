package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaCodec.BufferInfo;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.example.spectrumaudiofrequency.core.codec_manager.media_decoder.dbDecoderManager;
import com.example.spectrumaudiofrequency.core.codec_manager.media_decoder.dbDecoderManager.MediaSpecs;

import java.util.ArrayList;

import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;

/**
 * Extended Code manager, but contains methods for obtaining samples asynchronously.
 * The samples are saved in a database.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DecoderManagerWithStorage extends DecoderManager {
    private final ArrayList<PeriodRequest> RequestsPromises = new ArrayList<>();
    private dbDecoderManager dbOfDecoder;

    public DecoderManagerWithStorage(Context context, int ResourceId) {
        super(context, ResourceId);
        PrepareDataBase();
    }

    public DecoderManagerWithStorage(Context context, String AudioPath) {
        super(context, AudioPath);
        PrepareDataBase();
    }

    private void KeepPromises(DecoderResult decoderResult) {
        int size = RequestsPromises.size();
        int requestIndex = 0;
        for (int i = 0; i < size; i++) {
            PeriodRequest request = RequestsPromises.get(requestIndex);

            if (request.RequiredSampleId == decoderResult.SampleId) {
                RequestsPromises.remove(request);
                request.DecodingListener.onDecoded(decoderResult);
            } else if (request.RequiredSampleId < decoderResult.SampleId || IsDecoded) {
                RequestsPromises.remove(request);
                addRequest(request);
            } else {
                requestIndex++;
            }
        }
    }

    private void PrepareDataBase() {
        dbOfDecoder = new dbDecoderManager(context, MediaName);
        IsDecoded = dbOfDecoder.MediaIsDecoded(MediaName);
        if (IsDecoded) {
            MediaSpecs mediaSpecs = dbOfDecoder.getMediaSpecs();
            this.TrueMediaDuration = mediaSpecs.TrueMediaDuration;
            this.NewSampleDuration = (int) mediaSpecs.SampleDuration;
            this.NewSampleSize = mediaSpecs.SampleSize;
        }

        addDecodingListener(decoderResult -> {
            dbOfDecoder.addSamplePiece(decoderResult.SampleId, decoderResult.bytes);
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

    @Override
    public int getNumberOfSamples() {
        if (IsDecoded) return dbOfDecoder.getNumberOfSamples();
        else return super.getNumberOfSamples();
    }

    public int getSampleDuration() {
        return this.NewSampleDuration;
    }

    public void addRequest(PeriodRequest periodRequest) {
        byte[] dbSampleBytes = dbOfDecoder.getSamplePiece(periodRequest.RequiredSampleId);
        if (dbSampleBytes != null) {
            BufferInfo bufferInfo = new BufferInfo();
            bufferInfo.set(0, dbSampleBytes.length,
                    ((long) periodRequest.RequiredSampleId * NewSampleDuration),
                    BUFFER_FLAG_KEY_FRAME);
            periodRequest.DecodingListener.onDecoded(new
                    DecoderResult(periodRequest.RequiredSampleId, dbSampleBytes, bufferInfo));
        } else if (IsDecoded) {
            periodRequest.DecodingListener.onDecoded(new DecoderResult
                    (periodRequest.RequiredSampleId, new byte[0], null));
        } else RequestsPromises.add(periodRequest);
    }

    public void clear() {
        dbOfDecoder.deleteMediaDecoded(MediaName);
    }

    public void destroy() {
        dbOfDecoder.close();
    }

}