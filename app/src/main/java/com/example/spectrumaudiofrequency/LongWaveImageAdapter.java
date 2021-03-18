package com.example.spectrumaudiofrequency;

import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spectrumaudiofrequency.MediaDecoder.AudioDecoder;
import com.example.spectrumaudiofrequency.MediaDecoder.AudioDecoder.PeriodRequest;
import com.example.spectrumaudiofrequency.SinusoidConverter.SuperSimplifySinusoid;
import com.example.spectrumaudiofrequency.Util.CalculatePerformance.Performance;

import static com.example.spectrumaudiofrequency.MainActivity.InfoTextView;
import static com.example.spectrumaudiofrequency.Util.CalculatePerformance.SomePerformances;

public class LongWaveImageAdapter extends RecyclerView.Adapter<WaveViewHolder> {
    public com.example.spectrumaudiofrequency.MediaDecoder.AudioDecoder AudioDecoder;
    public WaveRender waveRender;

    public int WaveLength = 0;

    private final Util.CalculatePerformance RequestPerformance;
    private final Util.CalculatePerformance RenderPerformance;
    private static final int ImageResolution = 1;

    private WaveViewHolder holderObserved;

    private int Zoom;

    public void setZoom(int zoom) {
        Zoom = zoom;

        UpdateLength();
    }

    LongWaveImageAdapter(AudioDecoder audioDecoder, WaveRender waveRender) {
        this.AudioDecoder = audioDecoder;
        this.waveRender = waveRender;

        this.RequestPerformance = new Util.CalculatePerformance("RequestPerformance");
        this.RenderPerformance = new Util.CalculatePerformance("RenderPerformance");

        setZoom(3);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setWavePieceImageOnHolder(WaveViewHolder holder, final long Time,
                                           short[][] SampleChannels, long WavePieceDuration) {
        waveRender.render(holder.ImageBitmap, SampleChannels, Time, WavePieceDuration,
                (bitmap) -> holder.updateImage());
    }

    @NonNull
    @Override
    public WaveViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView WaveImageView = new ImageView(parent.getContext());
        RecyclerView.LayoutParams layoutParams = new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.MATCH_PARENT);

        WaveImageView.setLayoutParams(layoutParams);
        return new WaveViewHolder(WaveImageView, parent.getWidth() / ImageResolution, parent.getHeight() / ImageResolution);
    }

    interface getAudioPeriodsSimplifiedListener {
        void onResult(short[][] result);
    }

    void getAudioPeriodsSimplified(SuperSimplifySinusoid superSimplifySinusoid, long Time,
                                   int ObtainedPeriods, int NumberOfPeriods,
                                   getAudioPeriodsSimplifiedListener processListener) {
        AudioDecoder.addRequest(new PeriodRequest(Time,
                decoderResult -> {
                    superSimplifySinusoid.Simplify(AudioDecoder.bytesToSampleChannels(decoderResult.SamplesChannels));

                    if (ObtainedPeriods > NumberOfPeriods) {
                        processListener.onResult(superSimplifySinusoid.getSinusoidChannelSimplify());
                    } else {
                        getAudioPeriodsSimplified(superSimplifySinusoid,
                                Time + AudioDecoder.SampleDuration,
                                ObtainedPeriods + 1, NumberOfPeriods, processListener);
                    }
                }));
    }

    void getAudioPeriodsSimplified(long Time, int NumberOfPeriods,
                                   getAudioPeriodsSimplifiedListener processListener) {

        AudioDecoder.addRequest(new PeriodRequest(Time,
                decoderResult -> {
                    SuperSimplifySinusoid simplifySinusoid = new SuperSimplifySinusoid
                            (AudioDecoder.SampleSize / AudioDecoder.ChannelsNumber);
                    simplifySinusoid.Simplify(AudioDecoder.bytesToSampleChannels(decoderResult.SamplesChannels));

                    if (NumberOfPeriods == 1) {
                        processListener.onResult(simplifySinusoid.getSinusoidChannelSimplify());
                    } else {
                        getAudioPeriodsSimplified(simplifySinusoid,
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
                if (Time != decoderResult.SampleTime)
                    Log.e("setSampleError", "Time:" + Time + " decoderResultTime:" + decoderResult.SampleTime);
                setWavePieceImageOnHolder(waveViewHolder, Time, AudioDecoder.bytesToSampleChannels(decoderResult.SamplesChannels), AudioDecoder.SampleDuration);
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
    public void update(int Time) {
        if (inUpdate) return;
        inUpdate = true;

        RequestPerformance.start();
        AudioDecoder.addRequest(new PeriodRequest(Time, decoderResult -> {
            Performance requestPerformance = RequestPerformance.stop();

            RenderPerformance.start();
            waveRender.render(holderObserved.ImageBitmap, AudioDecoder.bytesToSampleChannels(decoderResult.SamplesChannels), Time,
                    AudioDecoder.SampleDuration, (bitmap) -> {

                        holderObserved.ImageBitmap = bitmap;
                        holderObserved.updateImage();
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
