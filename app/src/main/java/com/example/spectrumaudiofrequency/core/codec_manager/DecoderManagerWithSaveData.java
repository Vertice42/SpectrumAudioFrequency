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
public class DecoderManagerWithSaveData extends DecoderManager {
    private final ArrayList<PeriodRequest> RequestsPromises = new ArrayList<>();
    private dbDecoderManager dbOfDecoder;

    public DecoderManagerWithSaveData(Context context, int ResourceId) {
        super(context, ResourceId);
        PrepareDataBase();
    }

    public DecoderManagerWithSaveData(Context context, String AudioPath) {
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
                request.DecoderListener.OnProceed(decoderResult);
            } else if (request.RequiredSampleId < decoderResult.SampleId || DecodingFinish) {
                RequestsPromises.remove(request);
                addRequest(request);
            } else {
                requestIndex++;
            }
        }
    }

    private void PrepareDataBase() {
        dbOfDecoder = new dbDecoderManager(context, MediaName);
        DecodingFinish = dbOfDecoder.MediaIsDecoded(MediaName);
        if (DecodingFinish) {
            MediaSpecs mediaSpecs = dbOfDecoder.getMediaSpecs();
            this.TrueMediaDuration = mediaSpecs.TrueMediaDuration;
            this.NewSampleDuration = mediaSpecs.SampleDuration;
        }

        addOnDecodeListener(decoderResult -> {
            dbOfDecoder.addSamplePiece(decoderResult.SampleId, decoderResult.Sample);
            KeepPromises(decoderResult);
        });
        addOnEndListener(() -> {
            dbOfDecoder.setDecoded(new MediaSpecs(MediaName, TrueMediaDuration(),
                    NewSampleDuration));
        });
    }

    public void addRequest(PeriodRequest periodRequest) {
        byte[] dbSampleBytes = dbOfDecoder.getSamplePiece(periodRequest.RequiredSampleId);
        if (dbSampleBytes != null) {
            BufferInfo bufferInfo = new BufferInfo();
            bufferInfo.set(0, dbSampleBytes.length,
                    (long) (periodRequest.RequiredSampleId * NewSampleDuration),
                    BUFFER_FLAG_KEY_FRAME);
            periodRequest.DecoderListener.OnProceed(new DecoderResult(
                    (periodRequest.RequiredSampleId == dbOfDecoder.getSamplesLength() - 1),
                    periodRequest.RequiredSampleId,
                    dbSampleBytes, bufferInfo));
        } else if (DecodingFinish) {
            periodRequest.DecoderListener.OnProceed(new DecoderResult());
        } else RequestsPromises.add(periodRequest);
    }

    public void clear() {
        dbOfDecoder.deleteMediaDecoded(MediaName);
    }

    public void destroy() {
        dbOfDecoder.close();
    }

}