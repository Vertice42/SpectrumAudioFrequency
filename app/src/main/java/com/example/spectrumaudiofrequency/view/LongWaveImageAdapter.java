package com.example.spectrumaudiofrequency.view;

import android.annotation.SuppressLint;
import android.os.Build;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spectrumaudiofrequency.core.codec_manager.DecoderCodec;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderCodecWithCacheManager;
import com.example.spectrumaudiofrequency.sinusoid_converter.Rearrange.SuperSimplifySinusoid;
import com.example.spectrumaudiofrequency.util.CalculatePerformance;
import com.example.spectrumaudiofrequency.util.CalculatePerformance.Performance;

import static com.example.spectrumaudiofrequency.util.CalculatePerformance.SomePerformances;
import static com.example.spectrumaudiofrequency.view.activity.MainActivity.InfoTextView;

public class LongWaveImageAdapter extends RecyclerView.Adapter<WaveViewHolder> {
    public DecoderCodecWithCacheManager DecoderCodecWithCacheManager;
    public SinusoidDrawn sinusoidDrawn;

    public int WaveLength = 0;

    private final CalculatePerformance RequestPerformance;
    private final CalculatePerformance RenderPerformance;
    private static final int ImageResolution = 1;

    private WaveViewHolder holderObserved;

    private int Zoom;

    public void setZoom(int zoom) {
        Zoom = zoom;

        UpdateLength();
    }

    public LongWaveImageAdapter(DecoderCodecWithCacheManager decoderCodecWithCacheManager, SinusoidDrawn sinusoidDrawn) {
        this.DecoderCodecWithCacheManager = decoderCodecWithCacheManager;
        this.sinusoidDrawn = sinusoidDrawn;

        this.RequestPerformance = new CalculatePerformance("RequestPerformance");
        this.RenderPerformance = new CalculatePerformance("RenderPerformance");

        setZoom(1);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setWavePieceImageOnHolder(WaveViewHolder holder, final long Time,
                                           short[][] SampleChannels, long WavePieceDuration) {
        sinusoidDrawn.render(holder.ImageBitmap, SampleChannels, Time, WavePieceDuration,
                holder::updateImage);
    }

    @NonNull
    @Override
    public WaveViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView WaveImageView = new ImageView(parent.getContext());
        RecyclerView.LayoutParams layoutParams = new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.MATCH_PARENT);

        WaveImageView.setLayoutParams(layoutParams);
        return new WaveViewHolder(WaveImageView, parent.getWidth() / ImageResolution,
                parent.getHeight() / ImageResolution);
    }

    interface getAudioPeriodsSimplifiedListener {
        void onResult(short[][] result);
    }

    void nextAudioPeriodsToSimplify(SuperSimplifySinusoid superSimplifySinusoid, long Time,
                                    int ObtainedPeriods, int NumberOfPeriods,
                                    getAudioPeriodsSimplifiedListener processListener) {
        DecoderCodecWithCacheManager.addRequest(new DecoderCodec.PeriodRequest((int) (Time/25000), decoderResult -> {
            superSimplifySinusoid.Simplify(decoderResult.getSampleChannels(DecoderCodecWithCacheManager));

            if (ObtainedPeriods > NumberOfPeriods) {
                processListener.onResult(superSimplifySinusoid.getSinusoidChannelSimplify());
            } else {
                nextAudioPeriodsToSimplify(superSimplifySinusoid,
                        Time + 1,
                        ObtainedPeriods + 1, NumberOfPeriods, processListener);
            }
        }));
    }


    void getAudioPeriodsSimplified(long Time, int NumberOfPeriods,
                                   getAudioPeriodsSimplifiedListener processListener) {

        DecoderCodecWithCacheManager.addRequest(new DecoderCodec.PeriodRequest((int) (Time/25000),
                decoderResult -> {
                    short[][] sampleChannels = decoderResult.getSampleChannels(DecoderCodecWithCacheManager);

                    SuperSimplifySinusoid simplifySinusoid = new SuperSimplifySinusoid
                            (sampleChannels[0].length / Zoom);
                    simplifySinusoid.Simplify(sampleChannels);

                    if (NumberOfPeriods == 1) {
                        processListener.onResult(simplifySinusoid.getSinusoidChannelSimplify());
                    } else {
                        nextAudioPeriodsToSimplify(simplifySinusoid,
                                Time + +1,
                                1, NumberOfPeriods, processListener);
                    }
                }));
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void setSampleOnTimePosition(WaveViewHolder waveViewHolder, final long Time) {
        if (Zoom == 1) {
            RequestPerformance.start();

            DecoderCodecWithCacheManager.addRequest(new DecoderCodec.PeriodRequest((int) (Time/25000), decoderResult -> {

                Performance requestPerformance = RequestPerformance.stop();
                RenderPerformance.start();
                setWavePieceImageOnHolder(waveViewHolder, Time, decoderResult.getSampleChannels(DecoderCodecWithCacheManager),
                        +1);

                Performance renderPerformance = RenderPerformance.stop();
                InfoTextView.setText(SomePerformances("Total", new Performance[]
                        {requestPerformance, renderPerformance}).toString() +
                        requestPerformance.toString() + renderPerformance.toString());
            }));
        } else {
            getAudioPeriodsSimplified(Time, Zoom, result -> {
                setWavePieceImageOnHolder(waveViewHolder, Time, result,
                        +1 * Zoom);
            });
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void setSampleOnTimePosition(int TimePosition) {
        setSampleOnTimePosition(this.holderObserved, TimePosition);
    }

    boolean inUpdate = false;

    @SuppressLint("SetTextI18n")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void update(long Time) {
        if (inUpdate) return;
        inUpdate = true;

        RequestPerformance.start();
        DecoderCodecWithCacheManager.addRequest(new DecoderCodec.PeriodRequest((int) (Time/25000), decoderResult -> {
            Performance requestPerformance = RequestPerformance.stop();

            RenderPerformance.start();
            sinusoidDrawn.render(holderObserved.ImageBitmap,
                    decoderResult.getSampleChannels(DecoderCodecWithCacheManager), Time,
                    +1, (bitmap) -> {
                        holderObserved.updateImage(bitmap);
                        inUpdate = false;

                        Performance renderPerformance = RenderPerformance.stop();
                        InfoTextView.setText(SomePerformances("Total", new Performance[]
                                {requestPerformance, renderPerformance}).toString() +
                                requestPerformance.toString() + renderPerformance.toString());
                    });
        }));
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onBindViewHolder(@NonNull WaveViewHolder holder, int position) {
        this.holderObserved = holder;
        setSampleOnTimePosition(holder, position * +1);
    }

    @Override
    public int getItemCount() {
        return this.WaveLength;
    }

    void UpdateLength() {
        this.WaveLength = (int) (DecoderCodecWithCacheManager.MediaDuration / Zoom) / +1;
        this.notifyDataSetChanged();
    }
}
