package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.Context;
import android.media.MediaCodec.BufferInfo;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.example.spectrumaudiofrequency.core.codec_manager.media_decoder.dbDecoderManager;
import com.example.spectrumaudiofrequency.core.codec_manager.media_decoder.dbDecoderManager.MediaSpecs;

import java.util.ArrayList;

import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DecoderCodecWithCacheManager extends DecoderCodec {
    private final ArrayList<PeriodRequest> RequestsPromises = new ArrayList<>();
    private dbDecoderManager dbOfDecoder;

    public DecoderCodecWithCacheManager(Context context, int ResourceId) {
        super(context, ResourceId);
        PrepareDataBase();
    }

    public DecoderCodecWithCacheManager(Context context, String AudioPath) {
        super(context, AudioPath);
        PrepareDataBase();
    }

    private void KeepPromises(int SampleId, BufferInfo bufferInfo, byte[] sample) {
        int size = RequestsPromises.size();
        int requestIndex = 0;
        for (int i = 0; i < size; i++) {
            PeriodRequest request = RequestsPromises.get(requestIndex);

            if (request.RequiredSampleId == SampleId) {
                RequestsPromises.remove(request);
                request.DecoderListener.OnProceed(new DecoderResult(SampleId, sample, bufferInfo));
            } else if (request.RequiredSampleId < SampleId || WasDecoded) {
                RequestsPromises.remove(request);
                addRequest(request);
            } else {
                requestIndex++;
            }
        }
    }

    private void PrepareDataBase() {
        dbOfDecoder = new dbDecoderManager(context, MediaName);
        WasDecoded = dbOfDecoder.MediaIsDecoded(MediaName);

        addOnDecodeListener(decoderResult -> {
            dbOfDecoder.addSamplePiece(decoderResult.SampleId, decoderResult.Sample);
            KeepPromises(decoderResult.SampleId, decoderResult.bufferInfo, decoderResult.Sample);
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
                    periodRequest.RequiredSampleId * NewSampleDuration,
                    BUFFER_FLAG_KEY_FRAME);
            periodRequest.DecoderListener.OnProceed(new DecoderResult(
                    periodRequest.RequiredSampleId,
                    dbSampleBytes, bufferInfo));
        } else if (WasDecoded) {
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