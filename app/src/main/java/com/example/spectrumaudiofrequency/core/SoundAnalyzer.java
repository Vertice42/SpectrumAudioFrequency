package com.example.spectrumaudiofrequency.core;

import android.os.Build;
import android.util.Log;

import com.example.spectrumaudiofrequency.core.VideoMuxer.Cutoff;
import com.example.spectrumaudiofrequency.mediaDecoder.AudioDecoder;
import com.example.spectrumaudiofrequency.mediaDecoder.AudioDecoder.ProcessListener;
import com.example.spectrumaudiofrequency.core.SoundAnalyzer.AudioPeakAnalyzer.Peak;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ForkJoinPool;

public class SoundAnalyzer {
    private final ForkJoinPool Poll;
    private final AudioDecoder AudioDecoder;

    private SoundAnalyzerProgressListener progressListener;
    private ProcessListener processListener;

    private int Time;
    private int iterations;
    private int iterationsMax;
    private final int SpikesCollectionSize;

    public interface SoundAnalyzerListener {
        void OnFinishedAnalysis(Peak[] result);
    }

    public interface SoundAnalyzerProgressListener {
        void OnProgressChange(float progress);
    }

    public static class AudioPeakAnalyzer {
        private final long Duration;

        public static class Peak extends Cutoff {
            public short datum;

            public Peak(short datum, long time) {
                super(time, time + datum / 2);
                this.datum = datum;
            }

            public void update(short datum, long time) {
                this.datum = datum;
                this.starTime = time;
            }

            @Override
            public @NotNull String toString() {
                return "Peak{" +
                        "datum=" + datum +
                        ", time=" + starTime +
                        '}';
            }

            public @NotNull String toJson() {
                return '{' +
                        "\"datum\":" + datum +
                        ", \"time\":" + starTime +
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

        void analyzeData(short[] Data, long startDataTime, long DataDuration) {
            if (Data.length < 10) return;
            double datumDuration = DataDuration / (double) Data.length;

            int index = (int) (startDataTime / (this.Duration / this.peaks.length));

            for (int i = 0; i < Data.length; i++) {
                if (Math.abs(Data[i]) > Math.abs(this.peaks[index].datum))
                    this.peaks[index].update(Data[i],
                            startDataTime + (long) (i * datumDuration));
            }
        }
    }

    public SoundAnalyzer(AudioDecoder audioDecoder, int SpikesCollectionSize) {
        this.AudioDecoder = audioDecoder;
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

    public void start(SoundAnalyzerListener soundAnalyzerListener) {
        this.iterations = 0;
        this.iterationsMax = (int) ((AudioDecoder.MediaDuration / AudioDecoder.SampleDuration) / AudioDecoder.MediaDuration) + 1;
        this.Time = 0;
        AudioPeakAnalyzer audioPeakAnalyzer = new AudioPeakAnalyzer(this.SpikesCollectionSize, AudioDecoder.MediaDuration);
        this.processListener = decoderResult -> {

            if (Time != decoderResult.SampleTime)
                Log.e("Time is !==", Time + "!=" + decoderResult.SampleTime);

            short[][] sampleChannels = decoderResult.getSampleChannels(AudioDecoder);

            audioPeakAnalyzer.analyzeData(sampleChannels[0], Time, AudioDecoder.SampleDuration);

            Time += AudioDecoder.SampleDuration;
            if (Time >= AudioDecoder.MediaDuration - AudioDecoder.SampleDuration) {
                //Arrays.sort(audioPeakAnalyzer.peaks, (o1, o2) -> o2.datum - o1.datum);
                soundAnalyzerListener.OnFinishedAnalysis(audioPeakAnalyzer.peaks);
            } else {
                AudioDecoder.addRequest(new AudioDecoder.PeriodRequest
                        (Time, processListener));

                iterations++;
                if (iterations > iterationsMax) {
                    iterations = 0;
                    float progress = ((float) Time / (float) AudioDecoder.MediaDuration) * 100f;
                    if (progress > 98.5f) iterationsMax = 1;

                    Poll.execute(() -> progressListener.OnProgressChange(progress));
                }
            }
        };

        AudioDecoder.addRequest(new AudioDecoder.PeriodRequest(Time, processListener));
    }
}
