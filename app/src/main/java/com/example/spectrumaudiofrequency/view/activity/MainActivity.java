package com.example.spectrumaudiofrequency.view.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spectrumaudiofrequency.R;
import com.example.spectrumaudiofrequency.core.SoundAnalyzer;
import com.example.spectrumaudiofrequency.mediaDecoder.AudioDecoder;
import com.example.spectrumaudiofrequency.util.Files;
import com.example.spectrumaudiofrequency.view.LongWaveImageAdapter;
import com.example.spectrumaudiofrequency.view.SinusoidDrawn;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import static androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL;
import static com.example.spectrumaudiofrequency.core.SoundAnalyzer.AudioPeakAnalyzer.Peak.JsonStringToPeakArray;
import static com.example.spectrumaudiofrequency.core.SoundAnalyzer.AudioPeakAnalyzer.Peak.PeakArrayToJsonString;
import static com.example.spectrumaudiofrequency.util.Files.ReadJsonFile;
import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

public class MainActivity extends AppCompatActivity {
    static {
        System.loadLibrary("native-lib");
    }

    public static int MANAGE_EXTERNAL_STORAGE_REQUEST = 120;

    private Button playButton;

    @SuppressLint("StaticFieldLeak")
    public static TextView InfoTextView;

    public LongWaveImageAdapter WaveAdapter;

    private AudioDecoder Decoder;
    private MediaPlayer mediaPlayer;
    private SinusoidDrawn sinusoidDrawn;
    private View.OnClickListener onClickPlayButton;
    private ProgressBar AnalysisProgressBar;
    private TextView ProgressText;
    private LinearLayout ProgressLayout;
    private int Peak = 0;

    public void RequestPermissions(Activity activity) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE},
                    MANAGE_EXTERNAL_STORAGE_REQUEST);
        }
    }

    private static boolean onAnalysis = false;

    public void AnalyzeAudio(String AudioName) {
        if (onAnalysis) return;
        onAnalysis = true;

        ProgressLayout.setVisibility(View.VISIBLE);

        SoundAnalyzer soundAnalyzer = new SoundAnalyzer(Decoder, 20);
        soundAnalyzer.setOnProgressChange(progress -> AnalysisProgressBar.post(() -> {
            ProgressText.setText((progress + "%"));
            AnalysisProgressBar.setProgress((int) (progress));
        }));

        soundAnalyzer.start(peaks -> {
            ProgressLayout.post(() -> ProgressLayout.setVisibility(View.GONE));
            sinusoidDrawn.Peaks = peaks;
            Files.SaveJsonFile(this, AudioName, PeakArrayToJsonString(peaks));
            onAnalysis = false;
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        RequestPermissions(this);

        ProgressLayout = this.findViewById(R.id.ProgressLayout);
        AnalysisProgressBar = this.findViewById(R.id.AnalysisProgressBar);
        ProgressText = this.findViewById(R.id.ProgressText);
        Button goButton = this.findViewById(R.id.goButton);

        RecyclerView waveRecyclerView = this.findViewById(R.id.WaveRecyclerView);
        InfoTextView = this.findViewById(R.id.InfoTextView);
        playButton = this.findViewById(R.id.playButton);
        Button reanalyzeButton = this.findViewById(R.id.ReanalyzeButton);
        SeekBar scaleInput = this.findViewById(R.id.scaleInput);

        Decoder = new AudioDecoder(this, R.raw.choose, true);
        Decoder.prepare().join();

        sinusoidDrawn = new SinusoidDrawn(this, Decoder.MediaDuration);
        WaveAdapter = new LongWaveImageAdapter(Decoder, this.sinusoidDrawn);

        waveRecyclerView.setHasFixedSize(false);
        LinearLayoutManager linearLayoutManagerOfWaveRecyclerView
                = new LinearLayoutManager(this, HORIZONTAL, false);
        waveRecyclerView.setLayoutManager(linearLayoutManagerOfWaveRecyclerView);
        waveRecyclerView.setAdapter(WaveAdapter);

        int AudioResourceChoseId = R.raw.choose;

        String FileName = String.valueOf(AudioResourceChoseId);
        if (ReadJsonFile(this, FileName).equals("")) {//todo change to true validation
            AnalyzeAudio(FileName);
        } else {
            ProgressLayout.setVisibility(View.GONE);
            sinusoidDrawn.Peaks = JsonStringToPeakArray(ReadJsonFile(this, FileName));
        }

        reanalyzeButton.setOnClickListener(v -> AnalyzeAudio(FileName));

        goButton.setOnClickListener(v -> {
            if (Peak >= sinusoidDrawn.Peaks.length) Peak = 0;
            linearLayoutManagerOfWaveRecyclerView.scrollToPositionWithOffset((int)
                    (sinusoidDrawn.Peaks[Peak].time / Decoder.SampleDuration), 0);
            Peak++;
        });

        scaleInput.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int newScale, boolean fromUser) {
                if (newScale < 1) newScale = 1;
                WaveAdapter.setZoom(newScale);
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
            mediaPlayer.setDataSource(this, getUriFromResourceId(this, AudioResourceChoseId));

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

                                if (mediaPlayer.isPlaying()) {
                                    long currentTime = mediaPlayer.getCurrentPosition() * 10;
                                    int PeacePosition = (int)
                                            (currentTime / Decoder.SampleDuration);
                                    long restTime = currentTime - PeacePosition * Decoder.SampleDuration;

                                    int pixelTime = Decoder.SampleDuration / linearLayoutManagerOfWaveRecyclerView.getWidth();

                                    Log.i("!!!", "PeacePosition: " + PeacePosition);

                                    waveRecyclerView.post(() -> {
                                        int offset = 0;
                                        if (restTime > 0)
                                            offset = (int) (restTime / pixelTime) * -1;
                                        linearLayoutManagerOfWaveRecyclerView
                                                .scrollToPositionWithOffset(PeacePosition, offset);
                                    });
                                }
                            }
                        }, 0, 33);

                    });
                    mediaPlayer.prepareAsync();
                } else {
                    if (mediaPlayer.getCurrentPosition() > mediaPlayer.getDuration() / 1.9f)
                        mediaPlayer.reset();

                    mediaPlayer.start();
                }

            } else {
                mediaPlayer.pause();
            }

            isPlay[0] = !isPlay[0];
        };
        playButton.setOnClickListener(PlayWithMusic);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.sinusoidDrawn.destroy();
    }
}