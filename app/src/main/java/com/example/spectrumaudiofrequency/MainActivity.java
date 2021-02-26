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
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {
    static {
        System.loadLibrary("native-lib");
    }

    public static int MANAGE_EXTERNAL_STORAGE_REQUEST = 120;

    public ConstraintLayout MainView;
    public RecyclerView WaveRecyclerView;
    public Button startObserverButton;
    public Button playButton;
    public SeekBar ScaleInput;

    @SuppressLint("StaticFieldLeak")
    public static TextView InfoTextView;

    public LongWaveImageAdapter WaveAdapter;

    public AudioDecoder Decoder;
    public MediaPlayer mediaPlayer;
    private WaveRender waveRender;
    private View.OnClickListener Play;

    public void RequestPermissions(Activity activity) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE},
                    MANAGE_EXTERNAL_STORAGE_REQUEST);
        }
    }

    public void StartWatchingAudio(long AudioDuration) {
        WaveAdapter.WaveLength = (int) AudioDuration / WaveAdapter.getPeriod();//todo update on zomm change
        WaveAdapter.notifyDataSetChanged();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
        Array2Don1D array2Don1D = new Array2Don1D();

        for (int x = 0; x < array2Don1D.X_length; x++) {
            for (int y = 0; y < array2Don1D.Length; y++) {
                Array2Don1D.write2DArray(x, y, array2Don1D.Array1D, array2Don1D.Length, y);
                Log.i("Test", "X: " + x + " Y:" + Array2Don1D.read2DArray(x, y, array2Don1D.Array1D, array2Don1D.Length));
            }
        }*/

        setContentView(R.layout.activity_main);

        RequestPermissions(this);

        startObserverButton = this.findViewById(R.id.startObserverButton);
        WaveRecyclerView = this.findViewById(R.id.WaveRecyclerView);
        InfoTextView = this.findViewById(R.id.InfoTextView);
        playButton = this.findViewById(R.id.playButton);
        ScaleInput = this.findViewById(R.id.scaleInput);
        MainView = this.findViewById(R.id.MainView);

        String pkgName = getApplicationContext().getPackageName();
        Uri uri = Uri.parse("android.resource://" + pkgName + "/raw/" + R.raw.hollow);

        Decoder = new AudioDecoder(this, uri);

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setDataSource(this, uri);

        } catch (IOException e) {
            e.printStackTrace();
        }

        WaveRecyclerView.post(() -> {
            this.waveRender = new WaveRender(this, Decoder.getDuration());
            WaveAdapter = new LongWaveImageAdapter(Decoder, waveRender);

            ScaleInput.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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

            WaveRecyclerView.setHasFixedSize(false);
            WaveRecyclerView.setLayoutManager(new LinearLayoutManager(this,
                    LinearLayoutManager.HORIZONTAL, false));
            WaveRecyclerView.setAdapter(WaveAdapter);

            startObserverButton.setOnClickListener(v -> {
                startObserverButton.setOnClickListener(null);
                startObserverButton.setText("On Observation");

                StartWatchingAudio((int) Decoder.getDuration());
            });

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
                                    WaveAdapter.update((mediaPlayer.getCurrentPosition() * 1000 + WaveAdapter.getRenderMediaTimeMS()));
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
                        WaveAdapter.update(time.addAndGet(Decoder.bufferDuration/1000));
                    }
                }, 0, 100);
                playButton.setOnClickListener(v1 -> {
                    timer.cancel();
                    playButton.setOnClickListener(Play);
                });
            };

            playButton.setOnClickListener(Play);

        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.waveRender.destroy();
        Log.i("onDestroy", "WavesImagesAsClear" + WaveRender.Clear());
    }
}