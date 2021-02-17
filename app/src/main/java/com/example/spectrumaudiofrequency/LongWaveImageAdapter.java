package com.example.spectrumaudiofrequency;

import android.annotation.SuppressLint;
import android.os.Build;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Date;

import static com.example.spectrumaudiofrequency.MainActivity.InfoTextView;

public class LongWaveImageAdapter extends RecyclerView.Adapter<WaveViewHolder> {
    public int WaveLength = 0;

    public AudioDecoder AudioDecoder;
    public WaveRender waveRender;

    public int Zoom = 1;

    private static final int Resolution = 3;
    private static final int Period = 24000;

    int getPeriod() {
        return Period * Zoom;
    }

    private final EditText FrequencyInput;
    private WaveViewHolder holderObserved;

    LongWaveImageAdapter(AudioDecoder audioDecoder, WaveRender waveRender, EditText FrequencyInput) {
        this.AudioDecoder = audioDecoder;
        this.waveRender = waveRender;
        this.FrequencyInput = FrequencyInput;
    }

    protected class FFTAnimation {
        private int Frame = 0;
        private Thread thread;

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        public void start(WaveViewHolder holder, long Time, short[][] SamplesChannels, long WavePieceDuration) {
            Frame = 0;
            waveRender.Frequency = 10f;
            if (thread != null) thread.interrupt();
            thread = new Thread(() -> nextFrame(holder, Time, SamplesChannels, WavePieceDuration));
            thread.start();
        }

        public void stop() {
            if (thread != null) {
                thread.interrupt();
                thread = null;
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        private void nextFrame(WaveViewHolder holder, long Time, short[][] SamplesChannels, long WavePieceDuration) {
            waveRender.Frequency += 0.01f;
            waveRender.render(holder.ImageBitmap, SamplesChannels, Time, WavePieceDuration,
                    (bitmap) -> holder.imageView.post(() -> {
                        holder.ImageBitmap = bitmap;
                        holder.updateImage();
                        Frame++;
                        if (this.Frame < 20000)
                            nextFrame(holder, Time, SamplesChannels, WavePieceDuration);
                    }));
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setWavePieceImageOnHolder(WaveViewHolder holder, long Time,
                                           short[][] SampleChannels, long WavePieceDuration) {
        holder.imageView.setImageURI(null);
        waveRender.render(holder.ImageBitmap, SampleChannels, Time, WavePieceDuration,
                (bitmap) -> holder.updateImage());

        FFTAnimation fftAnimation = new FFTAnimation();

        holder.imageView.setOnLongClickListener(v -> {
            fftAnimation.start(holder, Time, SampleChannels, WavePieceDuration);
            return false;
        });

        holder.imageView.setOnClickListener((view) -> {
            fftAnimation.stop();
            FrequencyInput.setOnEditorActionListener((v, actionId, event) -> {

                if (event == null) return false;

                String sF = FrequencyInput.getText().toString();
                float f = 1;

                if (!sF.equals("")) {
                    f = Float.parseFloat(sF);
                    if (f < 0) f = 1f;
                }

                waveRender.Frequency = f;
                waveRender.render(holder.ImageBitmap, SampleChannels, Time, WavePieceDuration,
                        (bitmap) -> holder.updateImage());
                return false;
            });

        });
    }

    @NonNull
    @Override
    public WaveViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView WaveImageView = new ImageView(parent.getContext());
        RecyclerView.LayoutParams layoutParams = new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.MATCH_PARENT);

        WaveImageView.setLayoutParams(layoutParams);

        return new WaveViewHolder(WaveImageView, parent.getWidth() / Resolution, parent.getHeight() / Resolution);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void setPosition(int position) {
        setPosition(this.holderObserved, position);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void setPosition(WaveViewHolder waveViewHolder, int position) {
        long Time = position * getPeriod();
        AudioDecoder.addRequest(new AudioDecoder.PeriodRequest(Time, getPeriod(),
                (AudioChannels, SampleDuration) -> {
                    setWavePieceImageOnHolder(waveViewHolder, Time, AudioChannels, SampleDuration);
                }));
    }

    boolean inUpdate = false;
    int times = 0;
    int time = 0;
    private int RenderMediaTimeMS = 0;

    public int getRenderMediaTimeMS() {
        return RenderMediaTimeMS;
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void update(int Time) {
        if (inUpdate) return;
        inUpdate = true;
        long RequestStartTime = new Date().getTime();
        AudioDecoder.addRequest(new AudioDecoder.PeriodRequest(Time, getPeriod(), (AudioChannels, SampleDuration) -> {
            long RenderStartTime = new Date().getTime();
            long RequestTime = new Date().getTime() - RequestStartTime;

            waveRender.render(this.holderObserved.ImageBitmap, AudioChannels, Time, SampleDuration,
                    (bitmap) -> {
                        this.holderObserved.ImageBitmap = bitmap;
                        this.holderObserved.updateImage();
                        inUpdate = false;
                        if (times > 1000) {
                            times = 0;
                            time = 0;
                        }
                        time += (new Date().getTime() - RenderStartTime) + RequestTime;
                        times++;
                        RenderMediaTimeMS = time / times;
                        InfoTextView.setText("Time: " + Time + " RenderTime: " + (new Date().getTime() - RenderStartTime) + "ms"
                                + " RequestTime: " + RequestTime + "ms" + " Media:" + RenderMediaTimeMS + "ms");
                    });
        }));
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onBindViewHolder(@NonNull WaveViewHolder holder, int position) {
        setPosition(holder, position);
        this.holderObserved = holder;
    }

    @Override
    public int getItemCount() {
        return this.WaveLength;
    }
}
