package com.example.spectrumaudiofrequency;

import android.content.Context;

import androidx.renderscript.Allocation;
import androidx.renderscript.Element;
import androidx.renderscript.RenderScript;
import androidx.renderscript.Script;
import androidx.renderscript.Type;

import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import static com.example.spectrumaudiofrequency.Util.ConcatenateArray;
import static com.example.spectrumaudiofrequency.Util.getNumberOfCores;

public class SinusoidConverter {
    final static int PRECISION = 10;
    static int SPECTRUM_ANALYSIS_RAGE = 100;//todo erro de memoria se for muito grande
    private final ForkJoinPool forkJoinPool;

    SinusoidConverter(ForkJoinPool forkJoinPool) {
        this.forkJoinPool = forkJoinPool;
    }

    static float ToLogarithmicScale(short data) {
        if (data == 0) return 0;
        return (float) (((data < 0) ? Math.log10(data * -1) * -1 : (Math.log10(data))) * 100);
    }

    private interface Calculate {
        float[] CalculateFFT(int FrequencyRange, float[] Sample);
    }

    static class CalculatorFFT_GPU implements Calculate {

        @Override
        public float[] CalculateFFT(int FrequencyRange, float[] Sample) {
            return new float[0];
        }

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
        private final ForkJoinPool forkJoinPool;

        private int SampleLength = -1;
        private int AnglesLength = -1;

        CalculatorFFT_GPU(Context context, ForkJoinPool forkJoinPool) {
            this.rs = RenderScript.create(context);
            this.forkJoinPool = forkJoinPool;
        }

        public void ProcessFFT(GPU_FFTRequest gpu_fftRequest) {
            gpuFFTRequests.add(gpu_fftRequest);
            if (gpuFFTRequests.size() == 1) NextRequest();
        }

        private void NextRequest() {
            if (gpuFFTRequests.size() == 0) return;

            GPU_FFTRequest gpu_fftRequest = gpuFFTRequests.get(0);

            forkJoinPool.submit(() -> {
                gpu_fftRequest.resultListener.onRespond(CalculateFFT(
                        SPECTRUM_ANALYSIS_RAGE * PRECISION,
                        gpu_fftRequest.WavePeaceInput));
            });

            gpuFFTRequests.remove(gpu_fftRequest);

            NextRequest();
        }
    }

    static class CalculatorFFT_GPU_Default extends CalculatorFFT_GPU implements Calculate {
        private final ScriptC_fftGpu gpu;

        CalculatorFFT_GPU_Default(Context context, ForkJoinPool forkJoinPool) {
            super(context, forkJoinPool);
            this.gpu = new ScriptC_fftGpu(super.rs);
        }

        void CalculateAngles(float[] Java_Sample, int AnglesLength) {
            if (super.SampleLength == Java_Sample.length || super.AnglesLength == AnglesLength)
                return;

            super.SampleLength = Java_Sample.length;
            super.AnglesLength = AnglesLength;

            this.gpu.set_F32_SampleLength((float) Java_Sample.length);
            this.gpu.set_uint32_t_SampleLength(Java_Sample.length);
            this.gpu.set_PRECISION(PRECISION);
            this.gpu.set_AnglesLength(AnglesLength);

            Allocation angles = Allocation.createTyped(super.rs,
                    Type.createXY(super.rs, Element.F32(super.rs), AnglesLength, super.SampleLength),
                    Allocation.USAGE_SHARED);
            this.gpu.set_Angles(angles);

            this.gpu.forEach_CalculateAngles(angles, new Script.LaunchOptions()
                    .setX(0, AnglesLength).setY(0, 1));

            angles.copyTo(new float[AnglesLength * Java_Sample.length]);
        }

        @Override
        public float[] CalculateFFT(int FrequencyRange, float[] Sample) {
            if (Sample.length < 100) return new float[100];

            CalculateAngles(Sample, FrequencyRange);

            Allocation AllocationFFT = Allocation.createSized(super.rs, Element.F32(super.rs),
                    FrequencyRange, Allocation.USAGE_SCRIPT);
            Allocation alloc_sample = Allocation.createSized(super.rs, Element.F32(super.rs),
                    Sample.length, Allocation.USAGE_SHARED);

            alloc_sample.copy1DRangeFrom(0, Sample.length, Sample);
            gpu.bind_Sample(alloc_sample);

            gpu.forEach_fft(AllocationFFT);

            float[] fft = new float[FrequencyRange];
            AllocationFFT.copyTo(fft);
            return fft;
        }
    }

    static class CalculatorFFT_GPU_Adapted extends CalculatorFFT_GPU implements Calculate {
        private final ScriptC_fftGpuAdapted fftGpuAdapted;

        CalculatorFFT_GPU_Adapted(Context context, ForkJoinPool forkJoinPool) {
            super(context, forkJoinPool);
            this.fftGpuAdapted = new ScriptC_fftGpuAdapted(super.rs);
        }

        void CalculateAngles(float[] Java_Sample, int AnglesLength) {
            if (super.SampleLength == Java_Sample.length || super.AnglesLength == AnglesLength)
                return;

            super.SampleLength = Java_Sample.length;
            super.AnglesLength = AnglesLength;

            int AnglesAllocationLength = AnglesLength * Java_Sample.length;
            Allocation allocAngles = Allocation.createSized(super.rs, Element.F32(super.rs),
                    AnglesAllocationLength, Allocation.USAGE_SHARED);

            this.fftGpuAdapted.set_F32_SampleLength((float) Java_Sample.length);
            this.fftGpuAdapted.set_uint32_t_SampleLength(Java_Sample.length);
            this.fftGpuAdapted.set_AnglesLength(Java_Sample.length);
            this.fftGpuAdapted.set_PRECISION(PRECISION);
            this.fftGpuAdapted.set_AnglesLength(AnglesAllocationLength / AnglesLength);
            this.fftGpuAdapted.bind_Angles(allocAngles);

            this.fftGpuAdapted.forEach_CalculateAngles(allocAngles,
                    new Script.LaunchOptions().setX(0, AnglesLength));

            allocAngles.copyTo(new float[AnglesLength * Java_Sample.length]);
        }

        @Override
        public float[] CalculateFFT(int FrequencyRange, float[] Sample) {
            if (Sample.length < 100) return new float[100];

            Allocation AllocationFFT = Allocation.createSized(super.rs, Element.F32(super.rs), FrequencyRange);
            CalculateAngles(Sample, FrequencyRange);

            Allocation alloc_Sample = Allocation.createSized(super.rs, Element.F32(super.rs), Sample.length
                    , Allocation.USAGE_SHARED);
            alloc_Sample.copy1DRangeFrom(0, Sample.length, Sample);
            fftGpuAdapted.bind_Sample(alloc_Sample);

            fftGpuAdapted.forEach_fft(AllocationFFT);
            float[] fft = new float[FrequencyRange];
            AllocationFFT.copyTo(fft);

            return fft;
        }
    }

    private static native void CalculateAnglesOfFrequenciesRange(int anglesLength, int WavePieceLength);

    public static native float[] fftNative(int start, int end, short[] Sample);

    public static native void setPrecision(float Precision);

    public float[] fftNative(short[] Sample) {
        if (Sample.length < 100) return new float[100];
        setPrecision((float) PRECISION);
        CalculateAnglesOfFrequenciesRange(SPECTRUM_ANALYSIS_RAGE * PRECISION, Sample.length);
        int Cores = getNumberOfCores();

        int length = (SPECTRUM_ANALYSIS_RAGE * PRECISION) / Cores;

        //noinspection unchecked
        ForkJoinTask<float[]>[] fft_tasks = new ForkJoinTask[Cores];

        for (int i = 0; i < fft_tasks.length; i++) {
            int finalI = i;
            fft_tasks[i] = forkJoinPool.submit(() ->
                    fftNative(length * finalI, length * (finalI + 1), Sample));
        }

        float[][] CoresResults = new float[Cores][];
        for (int i = 0; i < CoresResults.length; i++) CoresResults[i] = fft_tasks[i].join();

        return ConcatenateArray(CoresResults);
    }
}