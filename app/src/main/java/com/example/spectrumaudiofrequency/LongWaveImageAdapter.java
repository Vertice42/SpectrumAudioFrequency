package com.example.spectrumaudiofrequency;

import android.annotation.SuppressLint;
import android.os.Build;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spectrumaudiofrequency.AudioDecoder.PeriodRequest;
import com.example.spectrumaudiofrequency.Util.CalculatePerformance.Performance;

import static com.example.spectrumaudiofrequency.MainActivity.InfoTextView;
import static com.example.spectrumaudiofrequency.Util.CalculatePerformance.SomePerformances;

public class LongWaveImageAdapter extends RecyclerView.Adapter<WaveViewHolder> {
    private final Util.CalculatePerformance RequestPerformance;
    private final Util.CalculatePerformance RenderPerformance;
    public int WaveLength = 0;

    public AudioDecoder AudioDecoder;
    public WaveRender waveRender;

    public int Zoom = 1;

    private static final int ImageResolution = 1;

    private WaveViewHolder holderObserved;

    LongWaveImageAdapter(AudioDecoder audioDecoder, WaveRender waveRender) {
        this.AudioDecoder = audioDecoder;
        this.waveRender = waveRender;

        this.RequestPerformance = new Util.CalculatePerformance("RequestPerformance");
        this.RenderPerformance = new Util.CalculatePerformance("RenderPerformance");
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setWavePieceImageOnHolder(WaveViewHolder holder, long Time,
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void setPosition(WaveViewHolder waveViewHolder, long Time) {
        AudioDecoder.addRequest(new PeriodRequest(Time, AudioDecoder.SampleDuration, decoderResult ->
                setWavePieceImageOnHolder(waveViewHolder, Time, decoderResult.SamplesChannels, decoderResult.SampleDuration)));
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void setPosition(int position) {
        setPosition(this.holderObserved, position);
    }

    boolean inUpdate = false;

    @SuppressLint("SetTextI18n")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void update(int Time) {
        if (inUpdate) return;
        inUpdate = true;

        RequestPerformance.start();
        AudioDecoder.addRequest(new PeriodRequest(Time, AudioDecoder.SampleDuration, decoderResult -> {
            Performance requestPerformance = RequestPerformance.stop();

            RenderPerformance.start();
            waveRender.render(holderObserved.ImageBitmap, decoderResult.SamplesChannels, Time,
                    decoderResult.SampleDuration, (bitmap) -> {

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
        setPosition(holder, position * AudioDecoder.SampleDuration);
    }

    @Override
    public int getItemCount() {
        return this.WaveLength;
    }
}
