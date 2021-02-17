package com.example.spectrumaudiofrequency;

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
    final static int PRECISION = 100;
    static int SPECTRUM_ANALYSIS_RAGE = 400;//todo erro de memoria se for muito grande
    static float ToLogarithmicScale(float data) {
        if (data == 0) return 0;
        return (float) (((data < 0) ? Math.log10(data * -1) * -1 : (Math.log10(data))) * 100);
    }

    private interface Calculate {
        float[] CalculateFFT(int FrequencyRange, short[] Sample);
    }

    static abstract class CalculatorFFT_GPU implements Calculate {
        static class GPU_FFTRequest {
            interface ResultListener {
                void onRespond(float[] result);
            }

            private final ResultListener resultListener;
            short[] SampleInput;

            GPU_FFTRequest(short[] SampleInput, ResultListener resultListener) {
                this.SampleInput = SampleInput;
                this.resultListener = resultListener;
            }
        }

        private final ArrayList<GPU_FFTRequest> gpuFFTRequests = new ArrayList<>();
        private final ForkJoinPool poll;

        private int SampleLength = -1;
        private int AnglesLength = -1;

        CalculatorFFT_GPU(ForkJoinPool poll) {
            this.poll = poll;
        }

        public void ProcessFFT(GPU_FFTRequest gpu_fftRequest) {
            gpuFFTRequests.add(gpu_fftRequest);
            if (gpuFFTRequests.size() == 1) NextRequest();
        }

        private void NextRequest() {
            if (gpuFFTRequests.size() == 0) return;

            GPU_FFTRequest gpu_fftRequest = gpuFFTRequests.get(0);

            float[] result = CalculateFFT(SPECTRUM_ANALYSIS_RAGE * PRECISION,
                    gpu_fftRequest.SampleInput);

            poll.submit(() -> gpu_fftRequest.resultListener.onRespond(result));

            gpuFFTRequests.remove(gpu_fftRequest);

            NextRequest();
        }
    }

    static class CalculatorFFT_GPU_Default extends CalculatorFFT_GPU implements Calculate {
        private final ScriptC_fftGpu gpu;
        private final RenderScript rs;

        CalculatorFFT_GPU_Default(RenderScript renderScript, ForkJoinPool forkJoinPool) {
            super(forkJoinPool);
            this.rs = renderScript;
            this.gpu = new ScriptC_fftGpu(renderScript);
        }

        void CalculateAngles(int SampleLength, int AnglesLength) {
            if (super.SampleLength == SampleLength || super.AnglesLength == AnglesLength)
                return;

            super.SampleLength = SampleLength;
            super.AnglesLength = AnglesLength;

            this.gpu.set_F32_SampleLength((float) SampleLength);
            this.gpu.set_uint32_t_SampleLength(SampleLength);
            this.gpu.set_PRECISION(PRECISION);
            this.gpu.set_AnglesLength(AnglesLength);

            Allocation angles = Allocation.createTyped(rs,
                    Type.createXY(rs, Element.F32(rs), AnglesLength, super.SampleLength),
                    Allocation.USAGE_SHARED);
            this.gpu.set_Angles(angles);

            this.gpu.forEach_CalculateAngles(angles, new Script.LaunchOptions()
                    .setX(0, AnglesLength).setY(0, 1));

            angles.copyTo(new float[AnglesLength * SampleLength]);
        }

        @Override
        public float[] CalculateFFT(int FrequencyRange, short[] Sample) {
            if (Sample.length < 100) return new float[100];

            CalculateAngles(Sample.length, FrequencyRange);

            Allocation AllocationFFT = Allocation.createSized(rs, Element.F32(rs),
                    FrequencyRange, Allocation.USAGE_SCRIPT);
            Allocation alloc_sample = Allocation.createSized(rs, Element.I16(rs),
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
        private final RenderScript rs;

        CalculatorFFT_GPU_Adapted(RenderScript renderScript, ForkJoinPool forkJoinPool) {
            super(forkJoinPool);
            this.rs = renderScript;
            this.fftGpuAdapted = new ScriptC_fftGpuAdapted(rs);
        }

        void CalculateAngles(int SampleLength, int AnglesLength) {
            if (super.SampleLength == SampleLength || super.AnglesLength == AnglesLength)
                return;

            super.SampleLength = SampleLength;
            super.AnglesLength = AnglesLength;

            int AnglesAllocationLength = AnglesLength * SampleLength;
            Allocation allocAngles = Allocation.createSized(rs, Element.F32(rs),
                    AnglesAllocationLength, Allocation.USAGE_SHARED);

            this.fftGpuAdapted.set_F32_SampleLength((float) SampleLength);
            this.fftGpuAdapted.set_uint32_t_SampleLength(SampleLength);
            this.fftGpuAdapted.set_AnglesLength(SampleLength);
            this.fftGpuAdapted.set_PRECISION(PRECISION);
            this.fftGpuAdapted.set_AnglesLength(AnglesAllocationLength / AnglesLength);
            this.fftGpuAdapted.bind_Angles(allocAngles);

            this.fftGpuAdapted.forEach_CalculateAngles(allocAngles,
                    new Script.LaunchOptions().setX(0, AnglesLength));

            allocAngles.copyTo(new float[AnglesLength * SampleLength]);
        }

        @Override
        public float[] CalculateFFT(int FrequencyRange, short[] Sample) {
            if (Sample.length < 100) return new float[100];

            Allocation AllocationFFT = Allocation.createSized(rs, Element.F32(rs), FrequencyRange);
            CalculateAngles(Sample.length, FrequencyRange);

            Allocation alloc_Sample = Allocation.createSized(rs, Element.I16(rs), Sample.length
                    , Allocation.USAGE_SHARED);
            alloc_Sample.copy1DRangeFrom(0, Sample.length, Sample);
            fftGpuAdapted.bind_Sample(alloc_Sample);

            fftGpuAdapted.forEach_fft(AllocationFFT);
            float[] fft = new float[FrequencyRange];
            AllocationFFT.copyTo(fft);

            return fft;
        }
    }

    static class CalculatorFFT_Native extends CalculatorFFT_GPU implements Calculate {

        CalculatorFFT_Native(ForkJoinPool poll) {
            super(poll);
        }

        @Override
        public float[] CalculateFFT(int FrequencyRange, short[] Sample) {
            if (Sample.length < 100) return new float[100];

            setPrecision((double) PRECISION);
            CalculateAnglesOfFrequenciesRange(SPECTRUM_ANALYSIS_RAGE * PRECISION, Sample.length);
            int Cores = getNumberOfCores();

            int length = (SPECTRUM_ANALYSIS_RAGE * PRECISION) / Cores;

            //noinspection unchecked
            ForkJoinTask<double[]>[] fft_tasks = new ForkJoinTask[Cores];

            for (int i = 0; i < fft_tasks.length; i++) {
                int finalI = i;
                fft_tasks[i] = super.poll.submit(() ->
                        fftNative(length * finalI, length * (finalI + 1), Sample));
            }

            double[][] CoresResults = new double[Cores][];
            for (int i = 0; i < CoresResults.length; i++) CoresResults[i] = fft_tasks[i].join();

            return Util.toFloat(ConcatenateArray(CoresResults));//todo deveriar retornar double
        }

    }

    private static native void CalculateAnglesOfFrequenciesRange(int anglesLength, int WavePieceLength);

    private static native double[] fftNative(int start, int end, short[] Sample);

    private static native void setPrecision(double Precision);

}