package com.example.spectrumaudiofrequency;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    static {
        System.loadLibrary("native-lib");
    }

    public static int MANAGE_EXTERNAL_STORAGE_REQUEST = 120;

    private RecyclerView WaveRecyclerView;
    private Button playButton;

    @SuppressLint("StaticFieldLeak")
    public static TextView InfoTextView;

    public LongWaveImageAdapter WaveAdapter;

    private AudioDecoder Decoder;
    private MediaPlayer mediaPlayer;
    private WaveRender waveRender;
    private View.OnClickListener Play;
    private ProgressBar AnalysisProgressBar;

    public void RequestPermissions(Activity activity) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE},
                    MANAGE_EXTERNAL_STORAGE_REQUEST);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        RequestPermissions(this);

        AnalysisProgressBar = this.findViewById(R.id.AnalysisProgressBar);
        WaveRecyclerView = this.findViewById(R.id.WaveRecyclerView);
        InfoTextView = this.findViewById(R.id.InfoTextView);
        playButton = this.findViewById(R.id.playButton);
        SeekBar scaleInput = this.findViewById(R.id.scaleInput);

        String pkgName = getApplicationContext().getPackageName();
        Uri uri = Uri.parse("android.resource://" + pkgName + "/raw/" + R.raw.hollow);

        Decoder = new AudioDecoder(this, uri);
        Decoder.prepare().join();

        WaveRecyclerView.post(() -> {
            waveRender = new WaveRender(this, Decoder.getDuration());
            WaveAdapter = new LongWaveImageAdapter(Decoder, waveRender);

            WaveRecyclerView.setHasFixedSize(false);
            WaveRecyclerView.setLayoutManager(new LinearLayoutManager
                    (this, LinearLayoutManager.HORIZONTAL, false));
            WaveRecyclerView.setAdapter(WaveAdapter);

            WaveAdapter.WaveLength = (int) Decoder.getDuration() / WaveAdapter.getPeriod();
            WaveAdapter.notifyDataSetChanged();
        });

        SoundAnalyzer soundAnalyzer = new SoundAnalyzer(Decoder);
        soundAnalyzer.setOnProgressChange(progress ->
        {
            AnalysisProgressBar.post(() -> {
                AnalysisProgressBar.setProgress((int) (progress));
                if (progress == 100f) AnalysisProgressBar.setVisibility(View.GONE);
            });

        });
        soundAnalyzer.start(peaks -> waveRender.Peaks = peaks);

        scaleInput.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int newScale, boolean fromUser) {
                if (newScale < 1) newScale = 1;
                WaveAdapter.Zoom = newScale;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setDataSource(this, uri);

        } catch (IOException e) {
            e.printStackTrace();
        }
        final boolean[] isPlay = {false};
        AtomicBoolean IsPapered = new AtomicBoolean(false);
        View.OnClickListener PlayWithMusic = v -> {
            if (!isPlay[0] && !mediaPlayer.isPlaying()) {
                if (!IsPapered.get()) {
                    mediaPlayer.setOnPreparedListener((mp) -> {
                        IsPapered.set(true);
                        mediaPlayer.start();

                        Timer timer = new Timer();
                        timer.scheduleAtFixedRate(new TimerTask() {
                            @Override
                            public void run() {
                                // if (mediaPlayer.isPlaying())
                                WaveAdapter.update((mediaPlayer.getCurrentPosition() * 1000));
                            }
                        }, 0, 16);

                    });
                    mediaPlayer.prepareAsync();
                } else {
                    if (mediaPlayer.getCurrentPosition() > mediaPlayer.getDuration() / 1.5f)
                        mediaPlayer.reset();

                    mediaPlayer.start();
                }

            } else {
                mediaPlayer.pause();
            }

            isPlay[0] = !isPlay[0];
        };

        this.Play = null;
        Play = v -> {
            Timer timer = new Timer();
            AtomicInteger time = new AtomicInteger();

            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    WaveAdapter.update(time.addAndGet(Decoder.SampleDuration / 1000));
                }
            }, 0, 100);
            playButton.setOnClickListener(v1 -> {
                timer.cancel();
                playButton.setOnClickListener(Play);
            });
        };

        playButton.setOnClickListener(Play);


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.waveRender.destroy();
        Log.i("onDestroy", "WavesImagesAsClear" + WaveRender.Clear());
    }
}