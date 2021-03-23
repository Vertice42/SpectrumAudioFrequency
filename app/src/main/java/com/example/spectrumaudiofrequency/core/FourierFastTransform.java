package com.example.spectrumaudiofrequency.core;

import androidx.renderscript.Allocation;
import androidx.renderscript.Element;
import androidx.renderscript.RenderScript;
import androidx.renderscript.Script;
import androidx.renderscript.Type;

import com.example.spectrumaudiofrequency.ScriptC_fftGpu;
import com.example.spectrumaudiofrequency.ScriptC_fftGpuAdapted;
import com.example.spectrumaudiofrequency.ScriptC_fftGpuPrecise;
import com.example.spectrumaudiofrequency.util.Array;
import com.example.spectrumaudiofrequency.util.CalculatePerformance;

import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

import static com.example.spectrumaudiofrequency.util.Array.ConcatenateArray;
import static com.example.spectrumaudiofrequency.util.CPU.getNumberOfCores;

public class FourierFastTransform {
    /**
     * Sets the precision of spectrum analysis by default to one decimal place.
     * Attention a very large tablet can cause out of memory errors.
     */
    public static int PRECISION = 200;
    /**
     * Defines the length of the Spectrum to be analyzed.
     * By default 400.
     * Attention a very large tablet can cause out of memory errors.
     */
    public static int SPECTRUM_ANALYSIS_RAGE = 100;

    private interface Calculate {
        /**
         * Method used internally in the class does not call
         */
        abstract float[] CalculateFFT(int FrequencyRange, short[] Sample);
    }

    /**
     * Abstract class to manage requirements for processing audio samples
     */
    public static abstract class FFTAbstract implements Calculate {
        static boolean CalculatePerformanceEnable = false;
        private CalculatePerformance gnuFFT_PerformanceTask;

        static class fftRequest {

            public final ForkJoinTask<float[]> task;
            public short[] SampleInput;

            fftRequest(FFTAbstract fft, short[] SampleInput) {
                this.SampleInput = SampleInput;
                task = new RecursiveTask<float[]>() {
                    @Override
                    protected float[] compute() {
                        return fft.CalculateFFT(SPECTRUM_ANALYSIS_RAGE * PRECISION, SampleInput);
                    }
                };
            }
        }

        private final ArrayList<fftRequest> fftRequests = new ArrayList<>();

        private final ForkJoinPool poll;

        private int SampleLength = -1;
        private int AnglesLength = -1;

        FFTAbstract(ForkJoinPool poll) {
            this.poll = poll;
        }

        /**
         * Add a request to the list of orders is awaited by the result. So the execution time may vary.
         */
        public float[] Transform(short[] Sample) {
            fftRequest gpu_fftRequest = new fftRequest(this, Sample);
            Transform(gpu_fftRequest);
            return gpu_fftRequest.task.join();
        }

        private void Transform(fftRequest task) {
            fftRequests.add(task);
            if (fftRequests.size() == 1) NextRequest();
        }

        private void RunWithPerformanceCalculation(ForkJoinTask<float[]> task) {
            if (this.gnuFFT_PerformanceTask == null)
                this.gnuFFT_PerformanceTask = new CalculatePerformance("gnuFFT_PerformanceTask", 100);
            gnuFFT_PerformanceTask.start();
            poll.execute(task);
            gnuFFT_PerformanceTask.stop().logPerformance();
        }

        private void Run(ForkJoinTask<float[]> task) {
            poll.execute(task);
        }

        private void NextRequest() {
            if (fftRequests.size() == 0) return;

            fftRequest gpuFFTRequest = this.fftRequests.get(0);
            if (FFTAbstract.CalculatePerformanceEnable)
                RunWithPerformanceCalculation(gpuFFTRequest.task);
            else Run(gpuFFTRequest.task);
            this.fftRequests.remove(gpuFFTRequest);

            NextRequest();
        }

    }

    /**
     * Calculate Fast Fourier Transform using the GPU.
     * Using the standard form of using multidimensional arrays in Render Script.
     */
    public static class Default extends FFTAbstract implements Calculate {
        private final ScriptC_fftGpu gpu;
        private final RenderScript rs;

        public Default(RenderScript renderScript, ForkJoinPool forkJoinPool) {
            super(forkJoinPool);
            this.rs = renderScript;
            this.gpu = new ScriptC_fftGpu(rs);
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

            alloc_sample.copyFrom(Sample);
            gpu.bind_Sample(alloc_sample);

            gpu.forEach_fft(AllocationFFT);

            float[] fft = new float[FrequencyRange];
            AllocationFFT.copyTo(fft);
            return fft;
        }
    }

    /**
     * Calculate Fast Fourier Transform using GPU.
     * The multi-referenced arrays are rearranged in one-dimensional arrays,
     * with a gain in performance in emulators,
     * all The performance in physical devices must be tested.
     */
    public static class Adapted extends FFTAbstract implements Calculate {
        private final ScriptC_fftGpuAdapted fftGpuAdapted;
        private final RenderScript rs;

        public Adapted(RenderScript renderScript, ForkJoinPool forkJoinPool) {
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

            Allocation alloc_sample = Allocation.createSized(rs, Element.I16(rs), Sample.length
                    , Allocation.USAGE_SHARED);
            alloc_sample.copyFrom(Sample);
            fftGpuAdapted.bind_Sample(alloc_sample);

            fftGpuAdapted.forEach_fft(AllocationFFT);
            float[] fft = new float[FrequencyRange];
            AllocationFFT.copyTo(fft);

            return fft;
        }
    }

    /**
     * Calculate Fast Fourier Transform using GPU.
     * Usually only the x coordinate of the fft is calculated,
     * on Adapted is calculated x and y and are added together,
     * but performance is reduced.
     */
    public static class Precise extends FFTAbstract implements Calculate {
        private final ScriptC_fftGpuPrecise fftGpuPrecise;
        private final RenderScript rs;

        public Precise(RenderScript renderScript, ForkJoinPool forkJoinPool) {
            super(forkJoinPool);
            this.rs = renderScript;
            this.fftGpuPrecise = new ScriptC_fftGpuPrecise(rs);
        }

        void CalculateAngles(int SampleLength, int AnglesLength) {
            if (super.SampleLength == SampleLength || super.AnglesLength == AnglesLength) return;

            super.SampleLength = SampleLength;
            super.AnglesLength = AnglesLength;

            int AnglesAllocLength = AnglesLength * SampleLength;
            Allocation allocAngles = Allocation.createSized(rs, Element.F32_2(rs),
                    AnglesAllocLength, Allocation.USAGE_SHARED);

            this.fftGpuPrecise.set_SampleLength(SampleLength);
            this.fftGpuPrecise.set_PRECISION(PRECISION);
            this.fftGpuPrecise.set_AnglesLength(AnglesAllocLength / AnglesLength);
            this.fftGpuPrecise.bind_Angles(allocAngles);

            this.fftGpuPrecise.forEach_CalculateAngles(allocAngles,
                    new Script.LaunchOptions().setX(0, AnglesLength));

            allocAngles.getStride();
        }

        @Override
        public float[] CalculateFFT(int FrequencyRange, short[] Sample) {
            if (Sample.length < 100) return new float[100];

            CalculateAngles(Sample.length, FrequencyRange);

            Allocation alloc_sample = Allocation.createSized(rs, Element.I16(rs), Sample.length
                    , Allocation.USAGE_SHARED);
            alloc_sample.copyFrom(Sample);
            fftGpuPrecise.bind_Sample(alloc_sample);

            Allocation AllocationFFT = Allocation.createSized(rs, Element.F32(rs), FrequencyRange);
            fftGpuPrecise.forEach_fft(AllocationFFT);
            float[] fft = new float[FrequencyRange];
            AllocationFFT.copyTo(fft);

            return fft;
        }
    }

    /**
     * Calculate Fast Fourier transform using the native C++ CPU,
     * the performance is reduced to long "SPECTRUM_ANALYSIS_RAGE",
     * but the performance is better for short "SPECTRUM_ANALYSIS_RAGE".
     */
    public static class Native extends FFTAbstract implements Calculate {
        static {
            System.loadLibrary("native-lib");
        }

        private static native void CalculateAnglesOfFrequenciesRange(int anglesLength, int WavePieceLength);

        private static native double[] fftNative(int start, int end, short[] Sample);

        private static native void setPrecision(double Precision);

        public Native(ForkJoinPool poll) {
            super(poll);
        }

        @Override
        public float[] CalculateFFT(int FrequencyRange, short[] Sample) {
            if (Sample.length < 100) return new float[100];

            setPrecision(PRECISION);
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

            return Array.toFloat(ConcatenateArray(CoresResults));//todo deveriar retornar double ou não desidase
        }
    }

}