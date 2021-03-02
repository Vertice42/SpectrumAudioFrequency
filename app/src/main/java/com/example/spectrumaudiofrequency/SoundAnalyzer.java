package com.example.spectrumaudiofrequency;

import android.util.Log;

import com.example.spectrumaudiofrequency.AudioDecoder.ProcessListener;
import com.example.spectrumaudiofrequency.SoundAnalyzer.AudioPeakAnalyzer.Peak;

import org.jetbrains.annotations.NotNull;

public class SoundAnalyzer {
    private SoundAnalyzerProgressListener progressListener;
    private ProcessListener processListener;
    private int Time;
    private final AudioDecoder Decoder;

    interface SoundAnalyzerListener {
        void OnFinishedAnalysis(Peak[] result);
    }

    interface SoundAnalyzerProgressListener {
        void OnProgressChange(float progress);
    }

    static class AudioPeakAnalyzer {
        private final long Duration;

        static class Peak {
            short datum;
            long time;

            public Peak(short datum, long time) {
                this.datum = datum;
                this.time = time;
            }

            @Override
            public @NotNull String toString() {
                return "Peak{" +
                        "datum=" + datum +
                        ", time=" + time +
                        '}';
            }
        }

        Peak[] peaks;

        AudioPeakAnalyzer(int SpikesCollectionSize, long Duration) {
            this.peaks = new Peak[SpikesCollectionSize];
            for (int i = 0; i < SpikesCollectionSize; i++)
                this.peaks[i] = new Peak((short) 0, 0);
            this.Duration = Duration;
        }

        void analyzeData(short[] Data, long startDataTime, long endDataTime) {
            if (Data.length < 10) return;
            long datumTime = (endDataTime - startDataTime) / Data.length;

            int index = (int) (endDataTime / (this.Duration / (this.peaks.length - 1f)));

            for (int i = 0; i < Data.length; i++) {
                if (Data[i] > Math.abs(this.peaks[index].datum))
                    this.peaks[index] = new Peak(Data[i], startDataTime + datumTime * i);
            }
        }

    /*
    void analyzeData(short[] Data, long startDataTime, long endDataTime) {
        if (Data.length < 10) return;
        long datumTime = (endDataTime - startDataTime) / Data.length;
        for (int i = 0; i < Data.length; i++) {
            int rank = -1;
            for (int j = 0; j < peaks.length; j++) {
                if (peaks[j] == null || Data[i] > Math.abs(peaks[j].datum)) rank = j;
                else break;
            }
            if (rank != -1)
                peaks[rank] = new Peak(Data[i], startDataTime + datumTime * i);

        }
    }
    */
    }

    public SoundAnalyzer(AudioDecoder decoder) {
        Decoder = decoder;
    }

    private void AnalyzePieceOfAudio(long time, int SampleDuration, ProcessListener processListener) {
        Decoder.addRequest(new AudioDecoder.PeriodRequest(time, SampleDuration, processListener));
    }

    public void setOnProgressChange(SoundAnalyzerProgressListener soundAnalyzerProgressListener) {
        this.progressListener = soundAnalyzerProgressListener;
    }

    void start(SoundAnalyzerListener soundAnalyzerListener) {

        this.Time = Decoder.SampleDuration;
        AudioPeakAnalyzer audioPeakAnalyzer = new AudioPeakAnalyzer(250, Decoder.getDuration());
        this.processListener = decoderResult -> {
            audioPeakAnalyzer.analyzeData(decoderResult.SamplesChannels[0], Time, Time + Decoder.SampleDuration);
            if (Time < Decoder.getDuration()) {
                AnalyzePieceOfAudio(Time, Decoder.SampleDuration, processListener);
                Time += Decoder.SampleDuration;
                this.progressListener.OnProgressChange(((float) Time / (float) Decoder.getDuration()) * 100f);

                if (Time >= Decoder.getDuration())
                    soundAnalyzerListener.OnFinishedAnalysis(audioPeakAnalyzer.peaks);
            }
        };

        AnalyzePieceOfAudio(Time, Decoder.SampleDuration, processListener);
    }
}
