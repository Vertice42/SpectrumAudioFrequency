package com.example.spectrumaudiofrequency.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.renderscript.RenderScript;

import com.example.spectrumaudiofrequency.core.SinusoidConverter;
import com.example.spectrumaudiofrequency.core.SinusoidConverter.CalculatorFFT;
import com.example.spectrumaudiofrequency.core.SinusoidConverter.CalculatorFFT__Adapted;
import com.example.spectrumaudiofrequency.core.SinusoidConverter.CalculatorFFT__Default;
import com.example.spectrumaudiofrequency.core.SinusoidConverter.CalculatorFFT__Precise;
import com.example.spectrumaudiofrequency.core.SinusoidConverter.CalculatorFFT_Native;
import com.example.spectrumaudiofrequency.core.SoundAnalyzer.AudioPeakAnalyzer.Peak;

import java.io.File;
import java.io.FileOutputStream;

import java.util.Date;
import java.util.concurrent.ForkJoinPool;

import static com.example.spectrumaudiofrequency.core.SinusoidConverter.SimplifySinusoid;

public class SinusoidDrawn {
    interface WaveRenderListeners {
        void onFinish(Bitmap bitmap);
    }

    public boolean AntiAlias = false;
    public Peak[] Peaks = new Peak[0];
    public long SpectrumSize;

    private static final String Folder = "WavePieces";

    private final RenderScript rs;
    private final ForkJoinPool pool;

    public int Height;
    public int Width;

    private final CalculatorFFT calculatorFFTGpu;

    public SinusoidDrawn(Context context, long SpectrumSize) {
        this.SpectrumSize = SpectrumSize;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.pool = ForkJoinPool.commonPool();
        } else {
            this.pool = new ForkJoinPool();
        }

        this.rs = RenderScript.create(context);

        switch (0) {
            case 0:
                this.calculatorFFTGpu = new CalculatorFFT__Adapted(rs, pool);
                break;
            case 1:
                this.calculatorFFTGpu = new CalculatorFFT__Precise(rs, pool);
                break;
            case 2:
                this.calculatorFFTGpu = new CalculatorFFT_Native(pool);
                break;
            default:
                this.calculatorFFTGpu = new CalculatorFFT__Default(rs, pool);

        }
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

    private void DrawFFT_Test(Canvas canvas, float[] sample, long Time, long PieceDuration, float Frequency) {
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

    private void DrawBeautifulWave(Canvas canvas, short[] sample, float Anchor, int color, float Press) {
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

            Path path = new Path();
            path.moveTo(startLineW, endLineH);
            path.quadTo(startLineW, startLineH, endLineW, endLineH);
            canvas.drawPath(path, WavePaint);

            //black main line
            startLineW += SpaceBetweenWaves;
            endLineW += SpaceBetweenWaves;
            //advances to the next wave point
        }
    }

    private void DrawTime(Canvas canvas, long Time, long PieceDuration, float Anchor) {
        int DrawNumber = 4;

        float timeDistance = PieceDuration / (float) DrawNumber;
        float pixelDistance = Width / (float) DrawNumber;

        Paint paint = new Paint();
        paint.setStrokeWidth(1);
        paint.setTextSize(10);
        paint.setColor(Color.BLACK);

        for (int i = 0; i < DrawNumber; i++) {
            canvas.drawText(((Time + i * timeDistance)) + "mic", i * pixelDistance, Anchor + Anchor / 10, paint);
        }
    }

    private void DrawAnalyzer(Canvas canvas, short[] Sample, long Time, long SampleDuration, float Anchor, float Press) {
        double pixelTime = (Width / (double) SampleDuration);

        Peak peakToDraw = null;
        for (Peak peak : this.Peaks) {
            if (peak.time >= Time && peak.time <= Time + SampleDuration) {
                peakToDraw = peak;
                break;
            }
        }

        if (peakToDraw == null) return;

        float Position = (float) ((peakToDraw.time - Time) * pixelTime);

        Paint textPaint = new Paint();
        textPaint.setStrokeWidth(20);
        textPaint.setTextSize(10);
        textPaint.setColor(Color.BLACK);
        canvas.drawText(peakToDraw.time + "mic", Position, Anchor + Anchor / 10, textPaint);
        canvas.drawText("datum: " + peakToDraw.datum, Position, Anchor + Anchor / 2, textPaint);

        Paint pointPaint = new Paint();
        pointPaint.setColor(Color.RED);
        pointPaint.setStrokeWidth(10);

        float amplitude = Anchor + peakToDraw.datum / Press;

        canvas.drawPoint(Position, amplitude, pointPaint);
    }

    private void DrawFFT(Canvas canvas, float[] fft, float Anchor, int color, float Press) {
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

    private void DrawSinusoid(Canvas canvas, short[] sample, float Anchor, int color, float Press) {
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

        float startLineH = sample[0];
        for (int i = 1; i < sample.length; i++) {
            startLineH = Anchor + startLineH / Press;
            float endLineH = Anchor + sample[i] / Press;
            //decreases the amplitude of the sound wave
            canvas.drawLine(startLineW, startLineH, endLineW, endLineH, WavePaint);
            //black main line
            startLineW += SpaceBetweenWaves;
            endLineW += SpaceBetweenWaves;
            startLineH = sample[i];
            //advances to the next wave point}
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
            float endLineH = sample[i];
            endLineH = (sample[i] > 0) ? endLineH * -1 : endLineH;
            float startLineH = sample[i - 1];
            startLineH = (sample[i - 1] < 0) ? startLineH * -1 : startLineH;

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

    public void render(Bitmap imageBitmap, short[][] SampleChannels, final long Time, long SampleDuration,
                       WaveRenderListeners onRenderFinish) {
        this.Width = imageBitmap.getWidth();
        this.Height = imageBitmap.getHeight();

        pool.execute(() -> {
            Canvas canvas = new Canvas(imageBitmap);
            canvas.drawColor(Color.WHITE);
            //Draw background

            DrawTime(canvas, Time, SampleDuration, Height / 20f);

            if (SampleChannels[0].length > 10) {
               // DrawAnalyzer(canvas, SampleChannels[0], Time, SampleDuration, Height / 2f, Height / 2f);

                DrawSinusoid(canvas, SampleChannels[0], Height / 2f, Color.BLACK, Height / 5f);

                //DrawWave(canvas, SimplifySinusoid(SampleChannels[0], Width), Height / 3f, Color.BLACK, Height / 2f);

                //DrawFFT(canvas, SinusoidConverter.SimplifySinusoid(calculatorFFTGpu.Process(SampleChannels[0]), Width), Height / 1.5f, Color.BLUE, Height / 20f);
            }
            onRenderFinish.onFinish(imageBitmap);
        });
    }

    public static boolean Clear() {
        return new File(Environment.getExternalStorageDirectory() + Folder).delete();
    }

    public void destroy() {
        this.rs.destroy();
    }

}