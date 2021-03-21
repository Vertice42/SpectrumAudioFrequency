package com.example.spectrumaudiofrequency.view;

import android.annotation.SuppressLint;
import android.os.Build;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spectrumaudiofrequency.sinusoid_converter.Rearrange.SuperSimplifySinusoid;
import com.example.spectrumaudiofrequency.mediaDecoder.AudioDecoder;
import com.example.spectrumaudiofrequency.mediaDecoder.AudioDecoder.PeriodRequest;
import com.example.spectrumaudiofrequency.util.CalculatePerformance;
import com.example.spectrumaudiofrequency.util.CalculatePerformance.Performance;

import static com.example.spectrumaudiofrequency.util.CalculatePerformance.SomePerformances;
import static com.example.spectrumaudiofrequency.view.activity.MainActivity.InfoTextView;

public class LongWaveImageAdapter extends RecyclerView.Adapter<WaveViewHolder> {
    public com.example.spectrumaudiofrequency.mediaDecoder.AudioDecoder AudioDecoder;
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

    public LongWaveImageAdapter(AudioDecoder audioDecoder, SinusoidDrawn sinusoidDrawn) {
        this.AudioDecoder = audioDecoder;
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
        AudioDecoder.addRequest(new PeriodRequest(Time,
                decoderResult -> {
                    superSimplifySinusoid.Simplify(AudioDecoder.bytesToSampleChannels(decoderResult.BytesSamplesChannels));

                    if (ObtainedPeriods > NumberOfPeriods) {
                        processListener.onResult(superSimplifySinusoid.getSinusoidChannelSimplify());
                    } else {
                        nextAudioPeriodsToSimplify(superSimplifySinusoid,
                                Time + AudioDecoder.SampleDuration,
                                ObtainedPeriods + 1, NumberOfPeriods, processListener);
                    }
                }));
    }

    void getAudioPeriodsSimplified(long Time, int NumberOfPeriods,
                                   getAudioPeriodsSimplifiedListener processListener) {

        AudioDecoder.addRequest(new PeriodRequest(Time,
                decoderResult -> {
                    short[][] sampleChannels = AudioDecoder.bytesToSampleChannels(decoderResult.BytesSamplesChannels);

                    SuperSimplifySinusoid simplifySinusoid = new SuperSimplifySinusoid
                            (sampleChannels[0].length / Zoom);
                    simplifySinusoid.Simplify(sampleChannels);

                    if (NumberOfPeriods == 1) {
                        processListener.onResult(simplifySinusoid.getSinusoidChannelSimplify());
                    } else {
                        nextAudioPeriodsToSimplify(simplifySinusoid,
                                Time + AudioDecoder.SampleDuration,
                                1, NumberOfPeriods, processListener);
                    }
                }));
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void setSampleOnTimePosition(WaveViewHolder waveViewHolder, final long Time) {
        if (Zoom == 1) {
            AudioDecoder.addRequest(new PeriodRequest(Time, decoderResult ->
            {
                setWavePieceImageOnHolder(waveViewHolder, Time,
                        AudioDecoder.bytesToSampleChannels(decoderResult.BytesSamplesChannels),
                        AudioDecoder.SampleDuration);
            }));
        } else {
            getAudioPeriodsSimplified(Time, Zoom, result -> {
                setWavePieceImageOnHolder(waveViewHolder, Time, result,
                        AudioDecoder.SampleDuration * Zoom);
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
        AudioDecoder.addRequest(new PeriodRequest(Time, decoderResult -> {
            Performance requestPerformance = RequestPerformance.stop();

            RenderPerformance.start();
            sinusoidDrawn.render(holderObserved.ImageBitmap,
                    AudioDecoder.bytesToSampleChannels(decoderResult.BytesSamplesChannels), Time,
                    AudioDecoder.SampleDuration, (bitmap) -> {
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
        setSampleOnTimePosition(holder, position * AudioDecoder.SampleDuration);
    }

    @Override
    public int getItemCount() {
        return this.WaveLength;
    }

    void UpdateLength() {
        this.WaveLength = (int) (AudioDecoder.MediaDuration / Zoom) / AudioDecoder.SampleDuration;
        this.notifyDataSetChanged();
    }
}
