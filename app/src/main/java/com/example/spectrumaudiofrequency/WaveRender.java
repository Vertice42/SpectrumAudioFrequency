package com.example.spectrumaudiofrequency;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.renderscript.RenderScript;

import com.example.spectrumaudiofrequency.SinusoidConverter.CalculatorFFT;
import com.example.spectrumaudiofrequency.SinusoidConverter.CalculatorFFT.GPUFFTTask;
import com.example.spectrumaudiofrequency.SinusoidConverter.CalculatorFFT__Adapted;
import com.example.spectrumaudiofrequency.SinusoidConverter.CalculatorFFT__Default;
import com.example.spectrumaudiofrequency.SinusoidConverter.CalculatorFFT__Precise;
import com.example.spectrumaudiofrequency.SinusoidConverter.CalculatorFFT_Native;
import com.example.spectrumaudiofrequency.Util.CalculatePerformance;

import java.io.File;
import java.io.FileOutputStream;

import java.util.Date;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

class WaveRender {

    private final RenderScript rs;
    private final CalculatePerformance gnuFFT_PerformanceTask;

    interface WaveRenderListeners {
        void onFinish(Bitmap bitmap);
    }

    private static final String Folder = "WavePieces";
    private final ForkJoinPool pool;

    public long SpectrumSize;

    public int Height;
    public int Width;

    public float Frequency = 1;

    public boolean AntiAlias = true;

    private float[] fftGpu;
    private float[] fftNative;

    private final CalculatorFFT calculatorFFTGpu;
    private final CalculatorFFT calculatorFFT_native;

    WaveRender(Context context, long SpectrumSize) {
        this.SpectrumSize = SpectrumSize;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.pool = ForkJoinPool.commonPool();
        } else {
            this.pool = new ForkJoinPool();
        }

        this.rs = RenderScript.create(context);

        calculatorFFT_native = new CalculatorFFT_Native(pool);

        switch (0) {
            case 0:
                this.calculatorFFTGpu = new CalculatorFFT__Adapted(rs, pool);
                break;
            case 1:
                this.calculatorFFTGpu = new CalculatorFFT__Precise(rs, pool);
                break;
            default:
                this.calculatorFFTGpu = new CalculatorFFT__Default(rs, pool);

        }

        this.gnuFFT_PerformanceTask = new CalculatePerformance("gnuFFT_PerformanceTask",100);
    }

    public static String getImageFileName(long ImageID) {
        return ImageID + ".jpg";
    }

    public static String getImageCacheAbsolutePath(long ImageID) {
        return Environment.getExternalStorageDirectory() +
                File.separator + Folder + File.separator + getImageFileName(ImageID);
    }

    private void SaveFileImage(long FrameID, Bitmap imageBitmap) {
        long SaveTime = new Date().getTime();
        boolean DirectoriesHasCreated = false;
        try {
            File myDir = new File(Environment.getExternalStorageDirectory() +
                    File.separator + Folder);
            if (!myDir.exists()) if (!myDir.mkdirs()) throw new Exception("myDir Error");
            File file = new File(myDir, getImageFileName(FrameID));
            FileOutputStream out = new FileOutputStream(file);
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
            imageBitmap.recycle();
        } catch (Exception e) {
            Log.e("DrawWave Error", "DirectoriesHasCreated:" + DirectoriesHasCreated + " CreateFile Error: ", e);
        }
        Log.i("TAG", "SaveTime: " + (new Date().getTime() - SaveTime) + "ms");
    }

    private void DrawTime(Canvas canvas, float Time, long PieceDuration, float Anchor) {
        if (PieceDuration < 1) PieceDuration = 1;
        float distance = ((float) PieceDuration / (float) this.Width) * 10f;

        //  Log.i("TAG", "Width:" + Width + "+ PieceDuration:" + PieceDuration + " distance: " + distance+" Time"+ Time);

        Paint paint = new Paint();
        paint.setStrokeWidth(1);
        paint.setTextSize(10);
        paint.setColor(Color.BLACK);

        float i = 0;
        while (i < this.Width) {
            canvas.drawText(((Time + i)) + "microns", i, Anchor + Anchor / 10, paint);
            i += distance;
        }
    }

    private void Draw_fft(Canvas canvas, float[] fft, float Anchor, int color, float Press) {
        float SpaceBetweenWaves = (float) Width / fft.length;
        //"spacing" determining the line spacing is by consequence the size of the sound wave
        Paint WavePaint = new Paint();
        WavePaint.setStrokeWidth(1);
        WavePaint.setColor(color);
        WavePaint.setAntiAlias(AntiAlias);
        //activates anti aliasing and sets the thickness of the line
        float startLineW = SpaceBetweenWaves, endLineW = SpaceBetweenWaves;
        //Initialise Lines
        for (float v : fft) {
            float amplitude = v;
            if (amplitude > 0) amplitude *= -1f;

            float EndLine = Anchor + amplitude / Press;
            //decreases the amplitude of the sound wave
            canvas.drawLine(startLineW, Anchor, endLineW, EndLine, WavePaint);
            //canvas.drawText(String.valueOf((int) EndLine), endLineW, endLineH - endLineH / 10, new Paint());
            startLineW += SpaceBetweenWaves;
            endLineW += SpaceBetweenWaves;
            //advances to the next wave point
        }
    }

    private void DrawFFT_Test(Canvas canvas, float[] sample, long Time, long PieceDuration) {
        if (sample.length < 1) return;

        //Log.i("TAG", "sample:" + sample.length + " Time:" + Time + "PieceDuration: " + PieceDuration + "microns");

        /*
        float byteTime = (float) sample.length / PieceDuration; // Microns

        int divisor = (int) (sample.length / (byteTime * 100000)); // Microns

        short[][] sample = Util.SplitArray(sample, divisor);

        short[] sample = sample[0];
        */


        int CenterX = (Width / 2);
        int CenterY = (Height / 2);

        final int radiusMax = CenterX / 10;

        Paint paint = new Paint();
        paint.setStrokeWidth(1f);
        paint.setColor(Color.MAGENTA);
        paint.setAntiAlias(AntiAlias);

        float DistanceBetweenPoints = (float) (2 * Math.PI) / (sample.length) * Frequency;

        float finalX = 0;
        float finalY = 0;

        float radius = (float) (sample[0] / radiusMax);

        float previousX = (float) Math.cos(0 * DistanceBetweenPoints) * radius + CenterX;
        float previousY = (float) Math.sin(0 * DistanceBetweenPoints) * radius + CenterY;

        for (int j = 1; j < sample.length; j++) {
            radius = (float) (sample[j] / radiusMax);

            float x = (float) Math.cos(j * DistanceBetweenPoints) * radius + CenterX;
            float y = (float) Math.sin(j * DistanceBetweenPoints) * radius + CenterY;

            finalX += x;
            finalY += y;

            canvas.drawLine(previousX, previousY, x, y, paint);

            previousX = x;
            previousY = y;
        }

        finalX /= sample.length;
        finalY /= sample.length;

        Paint PointPaint = new Paint();
        PointPaint.setColor(Color.BLACK);
        PointPaint.setStrokeWidth(1);

        canvas.drawPoint(finalX, finalY, PointPaint);

        canvas.drawText(finalX - CenterX + " C", 100, 100, paint);
        canvas.drawText(Frequency + " F", 100, 200, paint);

    }

    private void DrawWaveAmplitude(Canvas canvas, float[] sample, float Anchor, int color) {

        float SpaceBetweenWaves = (float) Width / (sample.length);
        //"spacing" determining the line spacing is by consequence the size of the sound wave
        Paint WavePaint = new Paint();
        WavePaint.setStrokeWidth(2);
        WavePaint.setColor(color);
        WavePaint.setAntiAlias(AntiAlias);
        //activates anti aliasing and sets the thickness of the line
        float startLineW = 0, endLineW = SpaceBetweenWaves;
        //Initialise Lines

        boolean change;
        boolean asPositive = false;

        for (int i = 1; i < sample.length; i++) {


            if (sample[i] != 0) {

                if (sample[i] > 0) {
                    change = !asPositive;
                    asPositive = true;
                } else {
                    change = asPositive;
                    asPositive = false;
                }

                if (change) {
                    WavePaint.setColor(Color.BLACK);
                } else if (asPositive) {
                    WavePaint.setColor(Color.BLUE);
                } else {
                    WavePaint.setColor(Color.GREEN);
                }

            } else {
                WavePaint.setColor(Color.GRAY);
            }

            float EndLine = SinusoidConverter.ToLogarithmicScale(sample[i]);
            float startLineH = SinusoidConverter.ToLogarithmicScale(sample[i - 1]);//todo é possivel reutilisar do loop anterior

            float endLineH = EndLine;

            startLineH = Anchor + startLineH / (Anchor / 50);
            endLineH = Anchor + endLineH / (Anchor / 50);
            //decreases the amplitude of the sound wave

            // canvas.drawText(String.valueOf((int) EndLine), endLineW, endLineH - endLineH / 10, new Paint());
            canvas.drawLine(startLineW, startLineH, endLineW, endLineH, WavePaint);
            //black main line
            startLineW += SpaceBetweenWaves;
            endLineW += SpaceBetweenWaves;
            //advances to the next wave point
        }
    }

    private void DrawWaveAmplitudeNegative(Canvas canvas, float[] sample, float Anchor, int color) {

        float SpaceBetweenWaves = (float) Width / (sample.length);
        //"spacing" determining the line spacing is by consequence the size of the sound wave
        Paint WavePaint = new Paint();
        WavePaint.setStrokeWidth(2);
        WavePaint.setColor(color);
        WavePaint.setAntiAlias(AntiAlias);
        //activates anti aliasing and sets the thickness of the line
        float startLineW = 0, endLineW = SpaceBetweenWaves;
        //Initialise Lines


        for (int i = 1; i < sample.length; i++) {
            float EndLine = SinusoidConverter.ToLogarithmicScale(sample[i]);
            float startLineH = SinusoidConverter.ToLogarithmicScale(sample[i - 1]);//todo é possivel reutilisar do loop anterior

            if (EndLine > 0) EndLine *= -1;
            if (startLineH > 0) startLineH *= -1;

            float endLineH = EndLine;

            startLineH = Anchor + startLineH / (Anchor / 20);
            endLineH = Anchor + endLineH / (Anchor / 20);
            //decreases the amplitude of the sound wave

            // canvas.drawText(String.valueOf((int) EndLine), endLineW, endLineH - endLineH / 10, new Paint());
            canvas.drawLine(startLineW, startLineH, endLineW, endLineH, WavePaint);
            //black main line
            startLineW += SpaceBetweenWaves;
            endLineW += SpaceBetweenWaves;
            //advances to the next wave point
        }
    }

    private void DrawWave(Canvas canvas, short[] sample, float Anchor, int color, float Press) {
        float SpaceBetweenWaves = (float) Width / sample.length;
        //"spacing" determining the line spacing is by consequence the size of the sound wave
        Paint WavePaint = new Paint();
        WavePaint.setStrokeWidth(1);
        WavePaint.setColor(color);
        WavePaint.setDither(false);
        WavePaint.setAntiAlias(AntiAlias);
        //activates anti aliasing and sets the thickness of the line
        float startLineW = 0, endLineW = SpaceBetweenWaves;
        //Initialise Lines
        for (int i = 1; i < sample.length; i++) {
            float EndLine = sample[i];
            EndLine = (sample[i] > 0) ? EndLine * -1 : EndLine;
            float startLineH = sample[i - 1];//todo é possivel reutilisar do loop anterior
            startLineH = (sample[i - 1] < 0) ? startLineH * -1 : startLineH;

            float endLineH = EndLine;

            startLineH = Anchor + startLineH / Press;
            endLineH = Anchor + endLineH / Press;
            //decreases the amplitude of the sound wave

            /*
            AmplitudePaint.setTextSize(10f);
            canvas.drawText(String.valueOf((int) EndLine), endLineW, endLineH - endLineH / 10, AmplitudePaint);
            */

            canvas.drawLine(startLineW, startLineH, endLineW, endLineH, WavePaint);
            //black main line
            startLineW += SpaceBetweenWaves;
            endLineW += SpaceBetweenWaves;
            //advances to the next wave point
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public class DrawWaves extends RecursiveTask<Bitmap> {

        private final Bitmap imageBitmap;
        private final short[][] sampleChannels;
        private final long SampleDuration;
        private long Time;
        private final WaveRenderListeners onRenderFinish;

        public DrawWaves(Bitmap imageBitmap, short[][] sampleChannels, long Time,
                         long SampleDuration, WaveRenderListeners onRenderFinish) {
            this.imageBitmap = imageBitmap;
            this.sampleChannels = sampleChannels;
            this.SampleDuration = SampleDuration;
            this.Time = Time;
            this.onRenderFinish = onRenderFinish;
        }

        @Override
        protected Bitmap compute() {
            Canvas canvas = new Canvas(imageBitmap);
            canvas.drawColor(Color.argb(255, 255, 255, 255));
            //Draw background

            DrawTime(canvas, Time, SampleDuration, Height / 20f);

            DrawWave(canvas, sampleChannels[0], Height / 4f, Color.BLACK, Height / 2f);

            Draw_fft(canvas, fftGpu, Height / 1.5f, Color.BLUE, Height / 20f);
            Draw_fft(canvas, fftNative, Height / 1.2f, Color.RED, Height / 20f);

            //Log.i("FFT length: ", "C:"+NativeFFT.length+" GPU:"+this.FFTWave.length);

            //DrawFFT_Test(canvas, wavePiece[0], Time, WavePieceDuration);
            //DrawWaveAmplitudeNegative(canvas,wavePiece[0],FFtWaveAnchor,Color.BLACK);

            onRenderFinish.onFinish(imageBitmap);
            return imageBitmap;
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void render(Bitmap imageBitmap, short[][] SampleChannels, long Time, long SampleDuration,
                       WaveRenderListeners onRenderFinish) {
        this.Width = imageBitmap.getWidth();
        this.Height = imageBitmap.getHeight();

        //todo redusir isso;
        gnuFFT_PerformanceTask.start();
        GPUFFTTask gpu_fftRequest = new GPUFFTTask(calculatorFFTGpu, SampleChannels[0]);
        calculatorFFTGpu.Process(gpu_fftRequest);
        this.fftGpu = gpu_fftRequest.task.join();
        gnuFFT_PerformanceTask.logPerformance();

        //todo redusir isso;

        pool.execute(new DrawWaves(imageBitmap, SampleChannels, Time, SampleDuration, onRenderFinish));
    }

    public static boolean Clear() {
        return new File(Environment.getExternalStorageDirectory() + Folder).delete();
    }

    public void destroy() {
        this.rs.destroy();
    }

}