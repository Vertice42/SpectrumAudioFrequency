package com.example.spectrumaudiofrequency.core;

import android.os.Build;

import com.example.spectrumaudiofrequency.core.MediaMuxerManager.Cutoff;
import com.example.spectrumaudiofrequency.core.SoundAnalyzer.AudioPeakAnalyzer.Peak;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.PeriodRequest;
import com.example.spectrumaudiofrequency.core.codec_manager.DecoderManagerWithStorage;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ForkJoinPool;

import static com.example.spectrumaudiofrequency.core.codec_manager.DecoderManager.DecoderResult.separateSampleChannels;

public class SoundAnalyzer {
    private final int SAMPLE_DURATION;
    private final ForkJoinPool Poll;
    private final DecoderManagerWithStorage decoderManagerWithStorage;
    private final AudioPeakAnalyzer audioPeakAnalyzer;
    private SoundAnalyzerProgressListener progressListener;

    public SoundAnalyzer(DecoderManagerWithStorage decoderManagerWithStorage,
                         int SpikesCollectionSize,
                         int SampleDuration) {
        this.decoderManagerWithStorage = decoderManagerWithStorage;
        this.SAMPLE_DURATION = SampleDuration;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.Poll = ForkJoinPool.commonPool();
        } else {
            this.Poll = new ForkJoinPool();
        }
        this.audioPeakAnalyzer = new AudioPeakAnalyzer(SpikesCollectionSize,
                decoderManagerWithStorage.MediaDuration);//todo mudar para true Mediaduration
    }

    public void setOnProgressChange(SoundAnalyzerProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    private void getAudioSampleAndAnalise(AudioPeakAnalyzer audioPeakAnalyzer, int SampleId) {
        decoderManagerWithStorage.addRequest(new PeriodRequest(SampleId, decoderResult -> {
            if (decoderResult.bufferInfo != null) {
                long sampleTime = decoderResult.bufferInfo.presentationTimeUs;
                short[][] sampleChannels = separateSampleChannels(decoderResult.bytes,
                        decoderManagerWithStorage.ChannelsNumber);

                audioPeakAnalyzer.analyzeData(sampleChannels[0], SampleId, SAMPLE_DURATION);
                long trueMediaDuration = decoderManagerWithStorage.getTrueMediaDuration();
                float progress = ((float) sampleTime / trueMediaDuration) * 100f;
                Poll.execute(() -> progressListener.OnProgressChange(progress));
                if (sampleTime < trueMediaDuration)
                    getAudioSampleAndAnalise(audioPeakAnalyzer, SampleId + 1);
            }
        }));
    }

    public void setOnFinish(SoundAnalyzerListener soundAnalyzerListener) {
        decoderManagerWithStorage.addFinishListener(() ->
                soundAnalyzerListener.OnFinishedAnalysis(audioPeakAnalyzer.peaks));
    }

    public void start() {
        if (!decoderManagerWithStorage.IsDecoded) decoderManagerWithStorage.start();
        getAudioSampleAndAnalise(audioPeakAnalyzer, 0);
    }

    public interface SoundAnalyzerListener {
        void OnFinishedAnalysis(Peak[] result);
    }

    public interface SoundAnalyzerProgressListener {
        void OnProgressChange(float progress);
    }

    public static class AudioPeakAnalyzer {
        private final long Duration;
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

        public static class Peak extends Cutoff {
            public short datum;

            public Peak(short datum, long time) {
                super(time, time + datum / 2);
                this.datum = datum;
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
        }
    }
}
