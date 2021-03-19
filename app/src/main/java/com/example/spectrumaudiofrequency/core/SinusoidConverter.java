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

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

import static com.example.spectrumaudiofrequency.util.Array.ConcatenateArray;
import static com.example.spectrumaudiofrequency.util.CPU.getNumberOfCores;

public class SinusoidConverter {
    final static int PRECISION = 10;
    static int SPECTRUM_ANALYSIS_RAGE = 400;//todo erro de memoria se for muito grande

    public static float ToLogarithmicScale(float data) {
        if (data == 0) return 0;
        return (float) (((data < 0) ? Math.log10(data * -1) * -1 : (Math.log10(data))) * 100);
    }

    private interface Calculate {
        float[] CalculateFFT(int FrequencyRange, short[] Sample);
    }

    public static abstract class CalculatorFFT implements Calculate {
        static boolean CalculatePerformanceEnable = false;
        private CalculatePerformance gnuFFT_PerformanceTask;

        //todo add asink metodo;
        static class fftRequest {

            public final ForkJoinTask<float[]> task;
            public short[] SampleInput;

            fftRequest(CalculatorFFT CalculatorFFT, short[] SampleInput) {
                this.SampleInput = SampleInput;
                task = new RecursiveTask<float[]>() {
                    @Override
                    protected float[] compute() {
                        return CalculatorFFT.CalculateFFT(SPECTRUM_ANALYSIS_RAGE * PRECISION, SampleInput);
                    }
                };
            }
        }

        private final ArrayList<fftRequest> fftRequests = new ArrayList<>();

        private final ForkJoinPool poll;

        private int SampleLength = -1;
        private int AnglesLength = -1;

        CalculatorFFT(ForkJoinPool poll) {
            this.poll = poll;
        }

        public float[] Process(short[] SampleChannels) {
            fftRequest gpu_fftRequest = new fftRequest(this, SampleChannels);
            Process(gpu_fftRequest);
            return gpu_fftRequest.task.join();
        }

        private void Process(fftRequest task) {
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
            if (CalculatorFFT.CalculatePerformanceEnable)
                RunWithPerformanceCalculation(gpuFFTRequest.task);
            else Run(gpuFFTRequest.task);
            this.fftRequests.remove(gpuFFTRequest);

            NextRequest();
        }

    }

    public static class CalculatorFFT__Default extends CalculatorFFT implements Calculate {
        private final ScriptC_fftGpu gpu;
        private final RenderScript rs;

        public CalculatorFFT__Default(RenderScript renderScript, ForkJoinPool forkJoinPool) {
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

            alloc_sample.copyFrom(Sample);
            gpu.bind_Sample(alloc_sample);

            gpu.forEach_fft(AllocationFFT);

            float[] fft = new float[FrequencyRange];
            AllocationFFT.copyTo(fft);
            return fft;
        }
    }

    public static class CalculatorFFT__Adapted extends CalculatorFFT implements Calculate {
        private final ScriptC_fftGpuAdapted fftGpuAdapted;
        private final RenderScript rs;

        public CalculatorFFT__Adapted(RenderScript renderScript, ForkJoinPool forkJoinPool) {
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

    public static class CalculatorFFT__Precise extends CalculatorFFT implements Calculate {
        private final ScriptC_fftGpuPrecise fftGpuPrecise;
        private final RenderScript rs;

        public CalculatorFFT__Precise(RenderScript renderScript, ForkJoinPool forkJoinPool) {
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

    public static class CalculatorFFT_Native extends CalculatorFFT implements Calculate {

        private static native void CalculateAnglesOfFrequenciesRange(int anglesLength, int WavePieceLength);

        private static native double[] fftNative(int start, int end, short[] Sample);

        private static native void setPrecision(double Precision);

        public CalculatorFFT_Native(ForkJoinPool poll) {
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

    /**
     * Decrease the Sample by averaging the values.
     * If the new length is greater than or equal to the old one,
     * returns the sample without modification.
     */
    public static short[] SimplifySinusoid(short @NotNull [] Sample, int NewLength) {
        if (NewLength >= Sample.length) return Sample;

        short[] result = new short[NewLength];
        int divider = Sample.length / NewLength;

        for (int i = 0; i < result.length; i++) {
            short media = 0;
            for (int j = 0; j < divider; j++) media += Sample[i * divider + j];
            media /= divider;

            result[i] = media;
        }
        return result;
    }

    public static float[] SimplifySinusoid(float @NotNull [] Sample, int NewLength) {
        if (NewLength >= Sample.length) return Sample;

        float[] result = new float[NewLength];
        int simplificationLength = Sample.length / NewLength;

        for (int i = 0; i < result.length; i++) {
            float media = 0;

            for (int j = 0; j < simplificationLength; j++)
                media += Sample[i * simplificationLength + j];

            media /= simplificationLength;

            result[i] = media;
        }
        return result;
    }

    public static class SuperSimplifySinusoid {
        ArrayList<ArrayList<Short>> SinusoidChannelSimplify = new ArrayList<>();
        private final int NewSampleLength;

        public SuperSimplifySinusoid(int NewSampleLength) {
            this.NewSampleLength = NewSampleLength;
        }

        public short[][] getSinusoidChannelSimplify() {
            short[][] SinusoidChannels = new short[SinusoidChannelSimplify.size()][];

            for (int i = 0; i < SinusoidChannels.length; i++) {
                ArrayList<Short> list = SinusoidChannelSimplify.get(i);
                SinusoidChannels[i] = new short[list.size()];
                for (int j = 0; j < SinusoidChannels[i].length; j++) {
                    SinusoidChannels[i][j] = SinusoidChannelSimplify.get(i).get(j);
                }
            }
            return SinusoidChannels;
        }

        public void Simplify(short[][] SampleChannels) {
            for (int channel = 0; channel < SampleChannels.length; channel++) {
                SinusoidChannelSimplify.add(new ArrayList<>());
                int simplificationLength = SampleChannels[channel].length / NewSampleLength;
                short media = 0;
                for (int i = 0; i < NewSampleLength; i++) {
                    for (int j = 0; j < simplificationLength; j++)
                        media += SampleChannels[channel][i * simplificationLength + j];
                    media /= simplificationLength;
                    SinusoidChannelSimplify.get(channel).add(media);
                }
            }
        }
    }
}