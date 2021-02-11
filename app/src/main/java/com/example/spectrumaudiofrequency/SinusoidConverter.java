package com.example.spectrumaudiofrequency;

import android.content.Context;
import android.util.Log;

import androidx.renderscript.Allocation;
import androidx.renderscript.Element;
import androidx.renderscript.RenderScript;

import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import static com.example.spectrumaudiofrequency.Util.ConcatenateArray;
import static com.example.spectrumaudiofrequency.Util.getNumberOfCores;

public class SinusoidConverter {

    final static float PRECISION = 10f;
    static int SPECTRUM_ANALYSIS_RAGE = 100;
    private final ForkJoinPool forkJoinPool;

    SinusoidConverter(ForkJoinPool forkJoinPool) {
        this.forkJoinPool = forkJoinPool;
    }

    static float ToLogarithmicScale(short data) {
        if (data == 0) return 0;
        return (float) (((data < 0) ? Math.log10(data * -1) * -1 : (Math.log10(data))) * 100);
    }

    static class FFT_GPUCalculator {

        static class GPU_FFTRequest {
            interface ResultListener {
                void onRespond(float[] result);
            }

            private final ResultListener resultListener;
            float[] WavePeaceInput;

            GPU_FFTRequest(float[] WavePeaceInput, ResultListener resultListener) {
                this.WavePeaceInput = WavePeaceInput;
                this.resultListener = resultListener;
            }
        }

        private final ArrayList<GPU_FFTRequest> gpuFFTRequests = new ArrayList<>();
        private final RenderScript rs;
        private final ScriptC_gpu gpu;

        private int WavePieceLength = -1;
        private int FrequencyRange = -1;

        FFT_GPUCalculator(Context context) {
            this.rs = RenderScript.create(context);
            this.gpu = new ScriptC_gpu(rs);
        }

        void CalculateAngles(float[] Java_Sample, Allocation fftReturns, int FrequencyRange) {
            if (WavePieceLength == Java_Sample.length
                    || this.FrequencyRange == FrequencyRange) return;

            WavePieceLength = Java_Sample.length;
            this.FrequencyRange = FrequencyRange;

            int AllocLength = FrequencyRange * Java_Sample.length;
            Allocation angles1DAllocation = Allocation.createSized(rs, Element.F32(rs), AllocLength, Allocation.USAGE_SHARED);
            this.gpu.set_F32_SampleLength((float) Java_Sample.length);
            this.gpu.set_I32_SampleLength(Java_Sample.length);
            this.gpu.set_PRECISION(PRECISION);
            this.gpu.set_AnglesLength(AllocLength / FrequencyRange);
            this.gpu.bind_Angles(angles1DAllocation);
            this.gpu.forEach_CalculateAngles(fftReturns);

            int bytesSize = angles1DAllocation.getBytesSize();
            angles1DAllocation.copyTo(new float[FrequencyRange * Java_Sample.length]);
            Log.i("GPU Angles Size", "" + bytesSize);

        }

        private float[] CalculateFFT(int FrequencyRange, float[] JavaSample) {
            if (JavaSample.length < 100) return new float[100];
            Allocation AllocationFFT = Allocation.createSized
                    (rs, Element.F32(rs), FrequencyRange, Allocation.USAGE_SCRIPT);

            CalculateAngles(JavaSample, AllocationFFT, FrequencyRange);

            Allocation sample = Allocation.createSized
                    (rs, Element.F32(rs), JavaSample.length, Allocation.USAGE_SHARED);
            sample.copy1DRangeFrom(0, JavaSample.length, JavaSample);
            gpu.bind_Sample(sample);

            float[] fft = new float[FrequencyRange];
            gpu.forEach_fft(AllocationFFT);
            AllocationFFT.copyTo(fft);
            return fft;
        }


        public void ProcessFFT(GPU_FFTRequest gpu_fftRequest) {
            gpuFFTRequests.add(gpu_fftRequest);
            if (gpuFFTRequests.size() == 1) NextRequest();
        }

        private void NextRequest() {
            if (gpuFFTRequests.size() == 0) return;

            GPU_FFTRequest gpu_fftRequest = gpuFFTRequests.get(0);
            gpu_fftRequest.resultListener.onRespond(
                    CalculateFFT(SPECTRUM_ANALYSIS_RAGE * (int) PRECISION,
                            gpu_fftRequest.WavePeaceInput));

            gpuFFTRequests.remove(gpu_fftRequest);

            NextRequest();
        }
    }

    private static native void CalculateAnglesOfFrequenciesRange(int anglesLength, int WavePieceLength);

    public static native float[] NativeFFT(int start, int end, short[] Sample);

    public float[] NativeFFT(short[] Sample) {
        CalculateAnglesOfFrequenciesRange(SPECTRUM_ANALYSIS_RAGE * (int) PRECISION, Sample.length);
        int Cores = getNumberOfCores();

        int length = SPECTRUM_ANALYSIS_RAGE * (int) PRECISION / Cores;

        //noinspection unchecked
        ForkJoinTask<float[]>[] fft_tasks = new ForkJoinTask[Cores];

        for (int i = 0; i < fft_tasks.length; i++) {
            int finalI = i;
            fft_tasks[i] = forkJoinPool.submit(() ->
                    NativeFFT(length * finalI, length * (finalI + 1), Sample));
        }

        float[][] CoresResults = new float[Cores][];
        for (int i = 0; i < CoresResults.length; i++) CoresResults[i] = fft_tasks[i].join();


        return ConcatenateArray(CoresResults);
    }
}
