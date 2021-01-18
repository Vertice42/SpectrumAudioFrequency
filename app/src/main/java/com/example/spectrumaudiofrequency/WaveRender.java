package com.example.spectrumaudiofrequency;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

interface WaveRenderListeners {
    void onFinish(Bitmap bitmap);
}

class WaveRender {
    private static final String Folder = "WavePieces";
    private final ForkJoinPool forkJoinPool;

    public long SpectrumSize;

    public int Height;
    public int Width;

    public float Frequency = 1;

    public boolean AntiAlias = false;
    private final Context context;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    WaveRender(Context context, long SpectrumSize) {
        this.context = context;
        this.SpectrumSize = SpectrumSize;
        this.forkJoinPool = new ForkJoinPool();

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
        paint.setTextSize(20);
        paint.setColor(Color.BLACK);

        float i = 0;
        while (i < this.Width) {
            canvas.drawText(((Time + i)) + "microns", i, Anchor, paint);
            i += distance;
        }
    }

    private void Draw_fft(Canvas canvas, float[] wavePiece, float Anchor, int color) {
        float SpaceBetweenWaves = (float) Width / wavePiece.length;
        //"spacing" determining the line spacing is by consequence the size of the sound wave
        Paint WavePaint = new Paint();
        WavePaint.setStrokeWidth(1);
        WavePaint.setColor(color);
        WavePaint.setAntiAlias(AntiAlias);
        //activates anti aliasing and sets the thickness of the line
        float startLineW = SpaceBetweenWaves, endLineW = SpaceBetweenWaves;
        //Initialise Lines
        for (int i = 1; i < wavePiece.length; i++) {
            float amplitude = wavePiece[i] / Anchor;
            if (amplitude > 0) amplitude *= -1;
            float EndLine = Anchor + amplitude;
            //decreases the amplitude of the sound wave
            canvas.drawLine(startLineW, Anchor, endLineW, EndLine, WavePaint);
            //canvas.drawText(String.valueOf((int) EndLine), endLineW, endLineH - endLineH / 10, new Paint());
            startLineW += SpaceBetweenWaves;
            endLineW += SpaceBetweenWaves;
            //advances to the next wave point
        }
    }

    private void DrawFFT_Test(Canvas canvas, short[] wavePiece, long Time, long PieceDuration) {
        if (wavePiece.length < 1) return;

        //Log.i("TAG", "wavePiece:" + wavePiece.length + " Time:" + Time + "PieceDuration: " + PieceDuration + "microns");

        /*
        float byteTime = (float) wavePiece.length / PieceDuration; // Microns

        int divisor = (int) (wavePiece.length / (byteTime * 100000)); // Microns

        short[][] wavePiece = Util.SplitArray(wavePiece, divisor);

        short[] wavePiece = wavePiece[0];
        */


        int CenterX = (Width / 2);
        int CenterY = (Height / 2);

        final int radiusMax = CenterX / 10;

        Paint paint = new Paint();
        paint.setStrokeWidth(1f);
        paint.setColor(Color.MAGENTA);
        paint.setAntiAlias(AntiAlias);

        float DistanceBetweenPoints = (float) (2 * Math.PI) / (wavePiece.length) * Frequency;

        float finalX = 0;
        float finalY = 0;

        float radius = (float) (wavePiece[0] / radiusMax);

        float previousX = (float) Math.cos(0 * DistanceBetweenPoints) * radius + CenterX;
        float previousY = (float) Math.sin(0 * DistanceBetweenPoints) * radius + CenterY;

        for (int j = 1; j < wavePiece.length; j++) {
            radius = (float) (wavePiece[j] / radiusMax);

            float x = (float) Math.cos(j * DistanceBetweenPoints) * radius + CenterX;
            float y = (float) Math.sin(j * DistanceBetweenPoints) * radius + CenterY;

            finalX += x;
            finalY += y;

            canvas.drawLine(previousX, previousY, x, y, paint);

            previousX = x;
            previousY = y;
        }

        finalX /= wavePiece.length;
        finalY /= wavePiece.length;

        Paint PointPaint = new Paint();
        PointPaint.setColor(Color.BLACK);
        PointPaint.setStrokeWidth(1);

        canvas.drawPoint(finalX, finalY, PointPaint);

        canvas.drawText(finalX - CenterX + " C", 100, 100, paint);
        canvas.drawText(Frequency + " F", 100, 200, paint);

    }

    private void DrawWaveAmplitude(Canvas canvas, short[] wavePiece, float Anchor, int color) {

        float SpaceBetweenWaves = (float) Width / (wavePiece.length);
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

        for (int i = 1; i < wavePiece.length; i++) {


            if (wavePiece[i] != 0) {

                if (wavePiece[i] > 0) {
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

            float EndLine = SinusoidConverter.ToLogarithmicScale(wavePiece[i]);
            float startLineH = SinusoidConverter.ToLogarithmicScale(wavePiece[i - 1]);//todo é possivel reutilisar do loop anterior

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

    private void DrawWaveAmplitudeNegative(Canvas canvas, short[] wavePiece, float Anchor, int color) {

        float SpaceBetweenWaves = (float) Width / (wavePiece.length);
        //"spacing" determining the line spacing is by consequence the size of the sound wave
        Paint WavePaint = new Paint();
        WavePaint.setStrokeWidth(2);
        WavePaint.setColor(color);
        WavePaint.setAntiAlias(AntiAlias);
        //activates anti aliasing and sets the thickness of the line
        float startLineW = 0, endLineW = SpaceBetweenWaves;
        //Initialise Lines


        for (int i = 1; i < wavePiece.length; i++) {
            float EndLine = SinusoidConverter.ToLogarithmicScale(wavePiece[i]);
            float startLineH = SinusoidConverter.ToLogarithmicScale(wavePiece[i - 1]);//todo é possivel reutilisar do loop anterior

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

    private void DrawWave(Canvas canvas, short[] wavePiece, float Anchor, int color) {
        float SpaceBetweenWaves = (float) Width / wavePiece.length;
        //"spacing" determining the line spacing is by consequence the size of the sound wave
        Paint WavePaint = new Paint();
        WavePaint.setStrokeWidth(2);
        WavePaint.setColor(color);
        WavePaint.setDither(false);
        WavePaint.setAntiAlias(AntiAlias);
        //activates anti aliasing and sets the thickness of the line
        float startLineW = 0, endLineW = SpaceBetweenWaves;
        //Initialise Lines
        for (int i = 1; i < wavePiece.length; i++) {
            float EndLine = wavePiece[i];
            EndLine = (wavePiece[i] > 0) ? EndLine * -1 : EndLine;
            float startLineH = wavePiece[i - 1];//todo é possivel reutilisar do loop anterior
            startLineH = (wavePiece[i - 1] < 0) ? startLineH * -1 : startLineH;

            float endLineH = EndLine;

            startLineH = Anchor + startLineH / (Anchor * 2);
            endLineH = Anchor + endLineH / (Anchor * 2);
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
        private final short[][] wavePiece;
        private final long WavePieceDuration;
        private long Time;
        private final WaveRenderListeners onRenderFinish;
        private SinusoidConverter.fftGPU fftgpu;

        public DrawWaves(Bitmap imageBitmap, short[][] wavePiece, long Time, long WavePieceDuration, WaveRenderListeners onRenderFinish) {
            this.imageBitmap = imageBitmap;
            this.wavePiece = wavePiece;
            this.WavePieceDuration = WavePieceDuration;
            this.Time = Time;
            this.onRenderFinish = onRenderFinish;
            this.fftgpu = new SinusoidConverter.fftGPU(context);

        }

        @Override
        protected Bitmap compute() {
            Canvas canvas = new Canvas(imageBitmap);

            canvas.drawColor(Color.argb(255, 255, 255, 255));
            //Draw background

            //DrawTime(canvas, Time, WavePieceDuration, Height / 20f);

            //DrawWaveAmplitude(canvas, wavePiece[0], ((Height / 3f)), Color.GREEN);
            //DrawWaveAmplitudeNegative(canvas, wavePiece[0], ((Height / 2.5f)), Color.GREEN);
            float FFtWaveAnchor = Height / 1.3f;

            long fftTime = System.nanoTime();

            float[] fftWave;
            int fftColor;
            if (new Random().nextBoolean()) {
                short[] s_fftWave = SinusoidConverter.C_fftArray(this.wavePiece[0]);
                fftWave = new float[s_fftWave.length];
                for (int i = 0; i < s_fftWave.length; i++) fftWave[i] = (float) s_fftWave[i];
                fftColor = Color.RED;
            } else {
                fftWave = fftgpu.calculate(this.wavePiece[0]);
                fftColor = Color.BLUE;
            }

            //Log.i("fft", Arrays.toString(fftWave));
            Log.i("fftTime", (System.nanoTime() - fftTime) / 1000000f + "ms");

            Draw_fft(canvas, fftWave, FFtWaveAnchor, fftColor);

            //DrawFFT_Test(canvas, wavePiece[0], Time, WavePieceDuration);
            //DrawWaveAmplitudeNegative(canvas,wavePiece[0],FFtWaveAnchor,Color.BLACK);
            DrawWave(canvas, wavePiece[0], Height / 5f, Color.BLACK);

            onRenderFinish.onFinish(imageBitmap);
            return imageBitmap;
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void render(Bitmap imageBitmap, short[][] WavePiece, long Time, long PieceDuration, WaveRenderListeners onRenderFinish) {
        this.Width = imageBitmap.getWidth();
        this.Height = imageBitmap.getHeight();

        DrawWaves drawWaves = new DrawWaves(imageBitmap, WavePiece, Time, PieceDuration, onRenderFinish);
        forkJoinPool.execute(drawWaves);

    }

    public static boolean Clear() {
        return new File(Environment.getExternalStorageDirectory() + Folder).delete();
    }

}