package com.example.spectrumaudiofrequency;

import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spectrumaudiofrequency.AudioDecoder.PeriodRequest;
import com.example.spectrumaudiofrequency.SinusoidConverter.SuperSimplifySinusoid;
import com.example.spectrumaudiofrequency.Util.CalculatePerformance.Performance;

import java.util.Arrays;

import static com.example.spectrumaudiofrequency.MainActivity.InfoTextView;
import static com.example.spectrumaudiofrequency.Util.CalculatePerformance.SomePerformances;

public class LongWaveImageAdapter extends RecyclerView.Adapter<WaveViewHolder> {
    private final Util.CalculatePerformance RequestPerformance;
    private final Util.CalculatePerformance RenderPerformance;


    public AudioDecoder AudioDecoder;
    public WaveRender waveRender;

    public int WaveLength = 0;

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

    interface getAudioPeriodsListener {
        void onResult(short[][] result);
    }

    void getAudioPeriodsSimpled(SuperSimplifySinusoid superSimplifySinusoid, long Time,
                                int ObtainedPeriods, int NumberOfPeriods,
                                getAudioPeriodsListener processListener) {
        AudioDecoder.addRequest(new PeriodRequest(Time, AudioDecoder.SampleDuration, decoderResult -> {
            superSimplifySinusoid.Simplify(decoderResult.SamplesChannels[0]);

            if (ObtainedPeriods > NumberOfPeriods) {
                //todo converter os dois canais
                short[][] shorts = new short[2][0];
                shorts[0] = superSimplifySinusoid.getResult();
                processListener.onResult(shorts);

            } else {
                getAudioPeriodsSimpled(superSimplifySinusoid,
                        Time + AudioDecoder.SampleDuration,
                        ObtainedPeriods + 1, NumberOfPeriods, processListener);
            }
        }));
    }

    void getAudioPeriodsSimpled(long Time, int NumberOfPeriods, getAudioPeriodsListener processListener) {

        getAudioPeriodsSimpled(new SuperSimplifySinusoid
                        (AudioDecoder.SampleSize / AudioDecoder.ChannelsNumber),
                Time, 0, NumberOfPeriods, processListener);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void setSampleOnTimePosition(WaveViewHolder waveViewHolder, final long Time) {
        if (Zoom == 1) {
            AudioDecoder.addRequest(new PeriodRequest(Time, AudioDecoder.SampleDuration, decoderResult ->
            {
                if (Time != decoderResult.SampleTime)
                    Log.e("setSampleError", "Time:" + Time + " decoderResultTime:" + decoderResult.SampleTime);
                setWavePieceImageOnHolder(waveViewHolder, Time, decoderResult.SamplesChannels, decoderResult.SampleDuration);
            }));
        } else {
            getAudioPeriodsSimpled(Time, Zoom, result -> {
                Log.i("SuperSimplifySinusoid", Arrays.toString(result[0]));
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
        setSampleOnTimePosition(holder, position * AudioDecoder.SampleDuration);
    }

    @Override
    public int getItemCount() {
        return this.WaveLength;
    }

    void UpdateLength() {
        this.WaveLength = (int) (AudioDecoder.getDuration() / Zoom) / AudioDecoder.SampleDuration;
        this.notifyDataSetChanged();
    }
}
