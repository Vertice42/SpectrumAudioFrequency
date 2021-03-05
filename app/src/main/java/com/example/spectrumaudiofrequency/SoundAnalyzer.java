package com.example.spectrumaudiofrequency;

import android.os.Build;

import com.example.spectrumaudiofrequency.AudioDecoder.ProcessListener;
import com.example.spectrumaudiofrequency.SoundAnalyzer.AudioPeakAnalyzer.Peak;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;

public class SoundAnalyzer {
    private final ForkJoinPool Poll;
    private SoundAnalyzerProgressListener progressListener;
    private ProcessListener processListener;
    private int Time;
    private final AudioDecoder Decoder;
    private int iterations;
    private int iterationsMax;
    private final int SpikesCollectionSize;

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

            public void update(short datum, long time) {
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

            public @NotNull String toJson() {
                return '{' +
                        "\"datum\":" + datum +
                        ", \"time\":" + time +
                        '}';
            }

            public static String PeakArrayToJsonString(Peak[] peak) {
                StringBuilder jsonString = new StringBuilder("{\"Peaks\":");
                jsonString.append("[");

                for (int i = 0; i < peak.length - 1; i++)
                    jsonString.append(peak[i].toJson()).append(", ");

                jsonString.append(peak[peak.length - 1].toJson());

                jsonString.append("] }");
                return jsonString.toString();
            }

            public static Peak[] JsonStringToPeakArray(String json) {
                Peak[] peaks = new Peak[0];
                try {
                    JSONArray array = new JSONObject(json).getJSONArray("Peaks");
                    peaks = new Peak[array.length()];
                    for (int i = 0; i < peaks.length; i++) {
                        JSONObject jsonObject = array.getJSONObject(i);
                        peaks[i] = new Peak(
                                (short) jsonObject.getInt("datum"),
                                jsonObject.getLong("time"));
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return peaks;
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
            double datumDuration = (double) (endDataTime - startDataTime) / Data.length;

            int index = (int) (startDataTime / (this.Duration / (this.peaks.length - 1f)));
            for (int i = 0; i < Data.length; i++) {
                if (Math.abs(Data[i]) > Math.abs(this.peaks[index].datum))
                    this.peaks[index].update(Data[i],
                            startDataTime + (long) (datumDuration * i));
            }
        }
    }

    public SoundAnalyzer(AudioDecoder decoder, int SpikesCollectionSize) {
        this.Decoder = decoder;
        this.SpikesCollectionSize = SpikesCollectionSize;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.Poll = ForkJoinPool.commonPool();
        } else {
            this.Poll = new ForkJoinPool();
        }
    }

    public void setOnProgressChange(SoundAnalyzerProgressListener soundAnalyzerProgressListener) {
        this.progressListener = soundAnalyzerProgressListener;
    }

    void start(SoundAnalyzerListener soundAnalyzerListener) {
        this.iterations = 0;
        this.iterationsMax = (int) ((Decoder.getDuration() / Decoder.SampleDuration) / Decoder.getDuration()) + 1;
        this.Time = 0;
        AudioPeakAnalyzer audioPeakAnalyzer = new AudioPeakAnalyzer(this.SpikesCollectionSize, Decoder.getDuration());
        this.processListener = decoderResult -> {
            Time += Decoder.SampleDuration;
            if (Time >= Decoder.getDuration() - Decoder.SampleDuration) {

                Arrays.sort(audioPeakAnalyzer.peaks, (o1, o2) -> o2.datum - o1.datum);
                soundAnalyzerListener.OnFinishedAnalysis(audioPeakAnalyzer.peaks);
                Decoder.setTimeOfExtractor(1);

            } else {
                audioPeakAnalyzer.analyzeData(decoderResult.SamplesChannels[0], Time, Time + Decoder.SampleDuration);
                Decoder.addRequest(new AudioDecoder.PeriodRequest(Time, Decoder.SampleDuration, processListener));


                iterations++;
                if (iterations > iterationsMax) {
                    iterations = 0;
                    float progress = ((float) Time / (float) Decoder.getDuration()) * 100f;
                    if (progress > 98.5f) iterationsMax = 1;

                    Poll.execute(() -> progressListener.OnProgressChange(progress));
                }
            }
        };

        Decoder.addRequest(new AudioDecoder.PeriodRequest(Time, Decoder.SampleDuration, processListener));
    }
}
