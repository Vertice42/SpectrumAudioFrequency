package com.example.spectrumaudiofrequency;

import android.content.Context;

import androidx.renderscript.Allocation;
import androidx.renderscript.Element;
import androidx.renderscript.RenderScript;

import com.android.rssample.ScriptC_gpu;

import static java.lang.Math.cos;
import static java.lang.Math.sin;


public class SinusoidConverter {

    static final float PRECISION = 10f;
    static final int SPECTRUM_ANALYSIS_SIZE = 16000;

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

    static short[] J_fftArray(short[] wavePiece) {
        short[] fftArray = new short[(int) (SPECTRUM_ANALYSIS_SIZE * PRECISION)];

        for (int i = 0; i < fftArray.length; i++) {
            fftArray[i] = J_FFT(wavePiece, i);
        }

        return fftArray;
    }

    static short[] CJ_fftArray(short[] wavePiece) {
        short[] fftArray = new short[(int) (SPECTRUM_ANALYSIS_SIZE * PRECISION)];

        for (int i = 0; i < fftArray.length; i++) {
            //  fftArray[i] = C_FFT(wavePiece, i);
        }
        return fftArray;
    }

    static class fftGPU {
        private final RenderScript rs;
        private final ScriptC_gpu gpu;
        boolean a = false;

        fftGPU(Context context) {
            this.rs = RenderScript.create(context);
            this.gpu = new ScriptC_gpu(rs);
        }

        float[] calculate(short[] J_WavePiece) {
            if (J_WavePiece.length < 20 && !a) return new float[0];
            a = true;

            //todo testar veolidade de codigo vazio

            Allocation input_data = Allocation.createSized(rs, Element.I16(rs), J_WavePiece.length, Allocation.USAGE_SCRIPT);
            input_data.copy1DRangeFrom(0, J_WavePiece.length, J_WavePiece);

            gpu.set_PRECISION(PRECISION);
            gpu.set_WavePieceLength(J_WavePiece.length);
            gpu.bind_WavePiece(input_data);

            int SPECTRUM = SPECTRUM_ANALYSIS_SIZE * (int) PRECISION;

            Allocation output_data = Allocation.createSized(rs, Element.F32(rs), SPECTRUM, Allocation.USAGE_SCRIPT);

            gpu.forEach_process(output_data);

            float[] java_out_data = new float[SPECTRUM];
            output_data.copyTo(java_out_data);

            input_data.destroy();
            output_data.destroy();

            a = false;
            return java_out_data;
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
