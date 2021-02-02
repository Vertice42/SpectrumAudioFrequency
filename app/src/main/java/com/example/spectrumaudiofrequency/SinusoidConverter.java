package com.example.spectrumaudiofrequency;

import android.content.Context;
import android.util.Log;

import androidx.renderscript.Allocation;
import androidx.renderscript.Element;
import androidx.renderscript.RenderScript;
import androidx.renderscript.Type;

import java.util.ArrayList;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

public class SinusoidConverter {

    static final float PRECISION = 10f;
    static final int SPECTRUM_ANALYSIS_RAGE = 16000;

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
        short[] fftArray = new short[(int) (SPECTRUM_ANALYSIS_RAGE * PRECISION)];

        for (int i = 0; i < fftArray.length; i++) {
            fftArray[i] = J_FFT(wavePiece, i);
        }

        return fftArray;
    }

    static short[] CJ_fftArray(short[] wavePiece) {
        short[] fftArray = new short[(int) (SPECTRUM_ANALYSIS_RAGE * PRECISION)];

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

        private ArrayList<GPU_FFTRequest> gpuFFTRequests = new ArrayList<>();
        private final RenderScript rs;
        private final ScriptC_gpu gpu;

        GPU(Context context) {
            this.rs = RenderScript.create(context);
            this.gpu = new ScriptC_gpu(rs);
        }

        private Allocation PreparePointsDistances(int FrequencyRange, int WavePieceLength) {
            Allocation output_data = Allocation.createSized(rs, Element.F32(rs), FrequencyRange, Allocation.USAGE_SHARED);
            this.gpu.set_WavePieceLength(WavePieceLength);
            this.gpu.set_PRECISION(PRECISION);
            this.gpu.forEach_CalculatePointsDistances(output_data);
            output_data.copyTo(new float[FrequencyRange]);
            return output_data;
        }

        private float[] CalculateFFT(float[] J_WavePiece) {
            if (J_WavePiece.length < 100) return new float[100];

            Allocation WavePiece = Allocation.createSized(rs, Element.F32(rs), J_WavePiece.length,Allocation.USAGE_SHARED);
            WavePiece.copy1DRangeFrom(0, J_WavePiece.length, J_WavePiece);

            //MemoryShared memoryShared = new MemoryShared(J_WavePiece);
            //for (int i = 0; i < SPECTRUM_ANALYSIS_RAGE; i++) memoryShared.use(i);

            return CalculateFFT(PreparePointsDistances(SPECTRUM_ANALYSIS_RAGE, J_WavePiece.length), WavePiece, J_WavePiece.length, SPECTRUM_ANALYSIS_RAGE);
        }

        static class MemoryShared {
            private final float[] array;

            MemoryShared(float[] array) {
                this.array = array;
            }

            private void use(int x) {
                float r = 0;
                int X = (int) x;

                int steps = (int) array.length - 1;
                if (X != 0 && X > steps) X = steps % X;
                int p = X;

                StringBuilder s = new StringBuilder();

                do {
                    r += array[X];// cos(p * PointsDistances[x]) * WavePiece[p] + sin(p * PointsDistances[x]) * WavePiece[p];
                    //s.append(" ").append(p);

                    p++;
                    if (p > steps) p = 0;
                } while (p != X);

                s.append(r/array.length);

                Log.i("X==" + x, s.toString());
            }
        }

        private float[] CalculateFFT(Allocation PointsDistances, Allocation WavePiece, int WavePieceLength, int FrequencyRange) {
            gpu.bind_PointsDistances(PointsDistances);
            gpu.bind_WavePiece(WavePiece);

            Allocation fftReturns = Allocation.createSized(rs, Element.F32(rs), FrequencyRange, Allocation.USAGE_SCRIPT);
            float[] floats = new float[FrequencyRange];

            gpu.forEach_fftArray(fftReturns);

            /*for (int Frequency = 0; Frequency < FrequencyRange; Frequency++) {
                gpu.set_Frequency(Frequency);
                gpu.bind_WavePiece(WavePiece);

                long MiniFFT = System.nanoTime();
                gpu.forEach_fft(fftReturns);

                float Time = ((System.nanoTime() - MiniFFT) / 1000000f);
                if (Time < 1 || Media == 0) {
                    Media += Time / Cont;
                    Cont++;
                }

                Log.i("MiniFFT", Time + "ms" + " Media:" + Media);
                if (Cont > 100) {
                    Cont = 1;
                    Media = 0;
                }
                floats[Frequency] = gpu.reduce_addfloat(fftReturns).get();

            }*/

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
            gpu_fftRequest.resultListener.onRespond(CalculateFFT(gpu_fftRequest.WavePeaceInput));
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

    static native short C_FFT(short[] J_WavePiece, int Frequency);

    static native short[] C_fftArray(short[] wavePiece);
}
