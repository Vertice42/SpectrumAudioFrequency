package com.example.spectrumaudiofrequency;

import android.content.Context;
import android.util.Log;

import androidx.renderscript.Allocation;
import androidx.renderscript.Element;
import androidx.renderscript.RenderScript;
import androidx.renderscript.Script;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

import static com.example.spectrumaudiofrequency.Util.ConcatenateArray;
import static com.example.spectrumaudiofrequency.Util.getNumberOfCores;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

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

    static short J_FFT(short[] wavePiece, int F) {
        float points_distance = (float) (((2 * Math.PI) / (float) wavePiece.length) * ((float) F) /
                PRECISION);

        float x_some = 0;
        float y_some = 0;

        for (int i = 0; i < wavePiece.length; i++) {
            float radius = (float) wavePiece[i];
            float f = (float) i;

            x_some += (cos(f * points_distance) * radius);
            y_some += (sin(f * points_distance) * radius);
        }

        float WavePieceLength_F = (float) wavePiece.length;

        x_some /= WavePieceLength_F;
        y_some /= WavePieceLength_F;

        return (short) ((x_some + y_some) * PRECISION);
    }

    static short[] J(short[] wavePiece) {
        short[] fftArray = new short[SPECTRUM_ANALYSIS_RAGE];

        for (int i = 0; i < fftArray.length; i++) {
            fftArray[i] = J_FFT(wavePiece, i);
        }

        return fftArray;
    }

    static short[] CJ_fftArray(short[] wavePiece) {
        short[] fftArray = new short[SPECTRUM_ANALYSIS_RAGE];

        for (int i = 0; i < fftArray.length; i++) {
            //  fftArray[i] = C_FFT(wavePiece, i);
        }
        return fftArray;
    }

    static class GPU {

        interface ResultListener {
            void onRespond(float[] result);
        }

        static class GPU_FFTRequest {
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

        private Allocation Angles1DAllocation;
        private int WavePieceLength = -1;

        GPU(Context context) {
            this.rs = RenderScript.create(context);
            this.gpu = new ScriptC_gpu(rs);
        }

        private float[] CalculateFFT(int FrequencyRange, float[] J_WavePiece) {
            if (J_WavePiece.length < 100) return new float[100];

            Allocation fftReturns = Allocation.createSized(rs, Element.F32(rs), FrequencyRange, Allocation.USAGE_SCRIPT);

            if (WavePieceLength != J_WavePiece.length) {
                WavePieceLength = J_WavePiece.length;
                int AllocLength = FrequencyRange * J_WavePiece.length;
                Angles1DAllocation = Allocation.createSized(rs, Element.F32(rs), AllocLength, Allocation.USAGE_SHARED);
                this.gpu.set_WavePieceLength(J_WavePiece.length);
                this.gpu.set_PRECISION(PRECISION);
                this.gpu.set_AnglesLength(AllocLength / FrequencyRange);
                this.gpu.bind_Angles(Angles1DAllocation);
                this.gpu.forEach_CalculateAngles(fftReturns);

                int bytesSize = Angles1DAllocation.getBytesSize();
                fftReturns.copyTo(new float[FrequencyRange]);
                Log.i("Angles Size", "" + bytesSize);
                //Prepare Angles
            }

            Allocation WavePiece = Allocation.createSized(rs, Element.F32(rs), J_WavePiece.length, Allocation.USAGE_SHARED);
            WavePiece.copy1DRangeFrom(0, J_WavePiece.length, J_WavePiece);
            gpu.bind_WavePiece(WavePiece);
            //set Wave
            float[] floats = new float[FrequencyRange];
            gpu.forEach_fftArray(fftReturns);
            fftReturns.copyTo(floats);
            return floats;
        }

        public void processFFT(GPU_FFTRequest gpu_fftRequest) {
            gpuFFTRequests.add(gpu_fftRequest);
            if (gpuFFTRequests.size() == 1) NextRequest();
        }

        private void NextRequest() {
            if (gpuFFTRequests.size() == 0) return;

            GPU_FFTRequest gpu_fftRequest = gpuFFTRequests.get(0);
            gpu_fftRequest.resultListener.onRespond(CalculateFFT(SPECTRUM_ANALYSIS_RAGE * (int) PRECISION, gpu_fftRequest.WavePeaceInput));
            gpuFFTRequests.remove(gpu_fftRequest);

            NextRequest();
        }
    }

    /*
    static class fftGPU{
        private final RenderScript rs;
        private final ScriptC_test gpu;
        boolean a = false;

        fftGPU(Context context){
            this.rs = RenderScript.create(context);
            this.gpu = new ScriptC_test(rs);
        }

         float[] calculate(short[] J_WavePiece) {
            if (J_WavePiece.length < 20 && !a) return new float[0];
            a = true;

            //todo testar veolidade de codigo vazio

            Allocation input_data = Allocation.createSized(rs, Element.I16(rs), J_WavePiece.length,Allocation.USAGE_SCRIPT);
            input_data.copy1DRangeFrom(0, J_WavePiece.length, J_WavePiece);

            gpu.set_PRECISION(PRECISION);
            gpu.set_WavePieceLength(J_WavePiece.length);
            gpu.bind_data(input_data);

            int SPECTRUM = (int) (SPECTRUM_ANALYSIS_SIZE * PRECISION);

            Allocation output_data = Allocation.createSized(rs, Element.F32(rs), SPECTRUM,Allocation.USAGE_SHARED);

            gpu.forEach_fft(output_data);

            float[] java_out_data = new float[SPECTRUM];
            output_data.copyTo(java_out_data);

            input_data.destroy();
            output_data.destroy();

            a = false;
            return java_out_data;
        }
    }
    */

    static native float C_FFT(short[] J_WavePiece, int Frequency);

    private static native float[] C_fftArray(int Length, short[] wavePiece);

    private static native float[] C_fftArray(int Offset, int Length, short[] wavePiece);


    float[] C_FFT(short[] wavePiece) {
        int Cores = getNumberOfCores();

        int length = SPECTRUM_ANALYSIS_RAGE * (int) PRECISION / Cores;

        //noinspection unchecked
        ForkJoinTask<float[]>[] fft_tasks = new ForkJoinTask[Cores];

        for (int i = 0; i < fft_tasks.length; i++) {
            int finalI = i;
            fft_tasks[i] = forkJoinPool.submit(() ->
                    C_fftArray(length * finalI, length * (finalI + 1), wavePiece));
        }

        float[][] CoresResults = new float[Cores][];
        for (int i = 0; i < CoresResults.length; i++) CoresResults[i] = fft_tasks[i].join();


        return ConcatenateArray(CoresResults);
    }
}
