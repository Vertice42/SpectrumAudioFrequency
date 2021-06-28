package com.example.spectrumaudiofrequency.view;

import android.annotation.SuppressLint;
import android.os.Build;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoderWithStorage;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoderWithStorage.PeriodRequest;
import com.example.spectrumaudiofrequency.sinusoid_manipulador.SinusoidResize.SuperResize;
import com.example.spectrumaudiofrequency.util.PerformanceCalculator;
import com.example.spectrumaudiofrequency.util.PerformanceCalculator.Performance;

import static com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoder.converterBytesToChannels;
import static com.example.spectrumaudiofrequency.util.PerformanceCalculator.SomePerformances;
import static com.example.spectrumaudiofrequency.view.activity.MainActivity.InfoTextView;

public class LongWaveImageAdapter extends RecyclerView.Adapter<WaveViewHolder> {
    private static final int ImageResolution = 2;
    private final PerformanceCalculator RequestPerformance;
    private final PerformanceCalculator RenderPerformanCalculator;
    public MediaDecoderWithStorage decoderManagerWithStorage;
    public SinusoidDrawn sinusoidDrawn;
    public int WaveLength = 0;
    boolean inUpdate = false;
    private WaveViewHolder holderObserved;
    private int Zoom;

    public LongWaveImageAdapter(MediaDecoderWithStorage decoderCodecWithCacheManager, SinusoidDrawn sinusoidDrawn) {
        this.decoderManagerWithStorage = decoderCodecWithCacheManager;
        this.sinusoidDrawn = sinusoidDrawn;

        this.RequestPerformance = new PerformanceCalculator("RequestPerformance");
        this.RenderPerformanCalculator = new PerformanceCalculator("RenderPerformance");

        setZoom(1);
    }

    public void setZoom(int zoom) {
        Zoom = zoom;

        UpdateLength();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setWavePieceImageOnHolder(WaveViewHolder holder, long period,
                                           short[][] SampleChannels, long WavePieceDuration) {
        sinusoidDrawn.render(holder.ImageBitmap,
                SampleChannels,
                period * decoderManagerWithStorage.getSampleDuration(),
                WavePieceDuration,
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

    void nextAudioPeriodsToResize(SuperResize superResize,
                                  final int position,
                                  int periodsSimplified,
                                  getAudioPeriodsSimplifiedListener processListener) {
        decoderManagerWithStorage.makeRequest(new PeriodRequest(position * Zoom + periodsSimplified, decoderResult -> {
            if (decoderResult.bufferInfo == null) {
                processListener.onResult(superResize.getSinusoidChannels());
                return;
            }

            superResize.resize(converterBytesToChannels(decoderResult.bytes,
                    decoderManagerWithStorage.ChannelsNumber));

            if (periodsSimplified > Zoom) {
                processListener.onResult(superResize.getSinusoidChannels());
            } else {
                nextAudioPeriodsToResize(superResize,
                        position,
                        periodsSimplified + 1,
                        processListener);
            }
        }));
    }


    void getAudioPeriodsSimplified(int position,
                                   getAudioPeriodsSimplifiedListener processListener) {

        decoderManagerWithStorage.makeRequest(new PeriodRequest(position * Zoom, decoderResult -> {
            short[][] sampleChannels = converterBytesToChannels(decoderResult.bytes,
                    decoderManagerWithStorage.ChannelsNumber);
            SuperResize simplifySinusoid = new SuperResize(sampleChannels[1].length / Zoom);
            simplifySinusoid.resize(sampleChannels);

            if (Zoom == 1) {
                processListener.onResult(simplifySinusoid.getSinusoidChannels());
            } else {
                nextAudioPeriodsToResize(simplifySinusoid,
                        position,
                        0,
                        processListener);
            }
        }));
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void setSampleOnTimePosition(WaveViewHolder waveViewHolder, int period) {
        int sampleDuration = decoderManagerWithStorage.getSampleDuration();
        if (Zoom == 1) {
            RequestPerformance.start();

            decoderManagerWithStorage.makeRequest(new PeriodRequest(period, decoderResult -> {
                Performance requestPerformance = RequestPerformance.stop();
                RenderPerformanCalculator.start();
                int channelsNumber = decoderManagerWithStorage.ChannelsNumber;
                setWavePieceImageOnHolder(waveViewHolder,
                        period,
                        converterBytesToChannels(decoderResult.bytes, channelsNumber),
                        decoderManagerWithStorage.getSampleDuration());

                Performance renderPerformance = RenderPerformanCalculator.stop();
                InfoTextView.setText(SomePerformances(RenderPerformanCalculator,
                        "Total",
                        new Performance[]
                                {requestPerformance, renderPerformance}).toString() +
                        requestPerformance.toString() + renderPerformance.toString());
            }));
        } else {
            getAudioPeriodsSimplified(period, result -> {
                setWavePieceImageOnHolder(waveViewHolder, period, result, sampleDuration);
            });
        }
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void update(long Time) {
        if (inUpdate) return;
        inUpdate = true;

        RequestPerformance.start();
        decoderManagerWithStorage.makeRequest(new PeriodRequest((int) (Time / decoderManagerWithStorage.getSampleDuration()), decoderResult -> {
            Performance requestPerformance = RequestPerformance.stop();

            RenderPerformanCalculator.start();
            sinusoidDrawn.render(holderObserved.ImageBitmap,
                    converterBytesToChannels(decoderResult.bytes,
                            decoderManagerWithStorage.ChannelsNumber), Time,
                    +1, (bitmap) -> {
                        holderObserved.updateImage(bitmap);
                        inUpdate = false;

                        Performance renderPerformance = RenderPerformanCalculator.stop();
                        InfoTextView.setText(SomePerformances(RenderPerformanCalculator,
                                "Total",
                                new Performance[]
                                        {requestPerformance, renderPerformance}).toString() +
                                requestPerformance.toString() + renderPerformance.toString());
                    });
        }));
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onBindViewHolder(@NonNull WaveViewHolder holder, int period) {
        this.holderObserved = holder;
        setSampleOnTimePosition(holder, period);
    }

    @Override
    public int getItemCount() {
        return this.WaveLength;
    }

    void UpdateLength() {
        this.WaveLength = decoderManagerWithStorage.getNumberOfSamples() / Zoom;
        this.notifyDataSetChanged();
    }

    interface getAudioPeriodsSimplifiedListener {
        void onResult(short[][] result);
    }
}
