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
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
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

public class MainActivity extends AppCompatActivity {
    static {
        System.loadLibrary("native-lib");
    }

    public static final String AUDIO_PATH = Environment.getExternalStorageDirectory() + "/Download/0.mp3";
    public static int MANAGE_EXTERNAL_STORAGE_REQUEST = 120;

    public ConstraintLayout MainView;
    public RecyclerView WaveRecyclerView;
    public Button startObserverButton;
    public Button playButton;
    public SeekBar ScaleInput;
    public EditText FrequencyInput;
    public static TextView InfoTextView;

    public LongWaveImageAdapter WaveAdapter;

    public AudioDecoder Decoder;
    public MediaPlayer mediaPlayer;

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

    static class Array2Don1D {
        int X_length = 40;
        int Y_length = 10;

        int ArrayLength = Y_length * X_length;

        int Length = ArrayLength / X_length;

        static void write2DArray(int x, int y, float[] array, int Length, float value) {
            array[Length * x + y] = value;
        }

        static float read2DArray(int x, int y, float[] array, int Length) {
            return array[Length * x + y];
        }

        float[] Array1D = new float[ArrayLength];
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Array2Don1D array2Don1D = new Array2Don1D();

        for (int x = 0; x < array2Don1D.X_length; x++) {
            for (int y = 0; y < array2Don1D.Length; y++) {
                Array2Don1D.write2DArray(x, y, array2Don1D.Array1D, array2Don1D.Length, y);
                Log.i("Test", "X: " + x + " Y:" + Array2Don1D.read2DArray(x, y, array2Don1D.Array1D, array2Don1D.Length));
            }
        }


        setContentView(R.layout.activity_main);

        RequestPermissions(this);

        WaveRecyclerView = this.findViewById(R.id.WaveRecyclerView);
        MainView = this.findViewById(R.id.MainView);
        startObserverButton = this.findViewById(R.id.observer);
        playButton = this.findViewById(R.id.playButton);
        ScaleInput = this.findViewById(R.id.scaleInput);
        FrequencyInput = this.findViewById(R.id.FrequencyInput);
        InfoTextView = this.findViewById(R.id.InfoTextView);

        Decoder = new AudioDecoder(AUDIO_PATH);
        mediaPlayer = new MediaPlayer();

        try {
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setDataSource(getApplicationContext(), Uri.parse(AUDIO_PATH));

        } catch (IOException e) {
            e.printStackTrace();
        }

        WaveRecyclerView.post(() -> {
            WaveRender waveRender = new WaveRender(this, Decoder.getDuration());
            WaveAdapter = new LongWaveImageAdapter(Decoder, waveRender, this.FrequencyInput);

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
            playButton.setOnClickListener((v -> {
                if (!isPlay[0] && !mediaPlayer.isPlaying()) {
                    if (!IsPapered.get()) {
                        mediaPlayer.setOnPreparedListener((mp) -> {
                            IsPapered.set(true);
                            mediaPlayer.start();

                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new TimerTask() {
                                @Override
                                public void run() {
                                    if (mediaPlayer.isPlaying())
                                        WaveAdapter.update((mediaPlayer.getCurrentPosition() * 1000+WaveAdapter.getRenderMediaTimeMS()));
                                }
                            }, 0, 15);

                        });
                        mediaPlayer.prepareAsync();
                    } else {
                        if (mediaPlayer.getCurrentPosition() > mediaPlayer.getDuration()/1.5f)
                            mediaPlayer.reset();

                        mediaPlayer.start();
                    }

                } else {
                    mediaPlayer.pause();
                }

                isPlay[0] = !isPlay[0];
            }));
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i("onDestroy", "WavesImagesAsClear" + WaveRender.Clear());
    }
}