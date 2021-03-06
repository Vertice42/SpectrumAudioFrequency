package com.example.spectrumaudiofrequency;

import android.content.Context;
import android.util.Log;

import androidx.renderscript.RenderScript;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.spectrumaudiofrequency.core.FourierFastTransform;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoderWithStorage;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoderWithStorage.PeriodRequest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

import static com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoder.converterBytesToChannels;
import static com.example.spectrumaudiofrequency.util.Array.calculateEquity;

@RunWith(AndroidJUnit4.class)
public class FourierFastTransformTest {

    private final FourierFastTransform.Native fft_Native;
    private final FourierFastTransform.Default fft_Default;
    private final FourierFastTransform.Adapted fft_Adapted;
    private final FourierFastTransform.Precise fft_Precise;
    private short[] Sample;
    private float[] ExpectedArray;

    public FourierFastTransformTest() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();

        RenderScript rs = RenderScript.create(context);

        MediaDecoderWithStorage decoder = new MediaDecoderWithStorage(context,
                R.raw.choose,
                0);

        fft_Default = new FourierFastTransform.Default(rs, ForkJoinPool.commonPool());
        fft_Native = new FourierFastTransform.Native(ForkJoinPool.commonPool());
        fft_Adapted = new FourierFastTransform.Adapted(rs, ForkJoinPool.commonPool());
        fft_Precise = new FourierFastTransform.Precise(rs, ForkJoinPool.commonPool());

        final CountDownLatch signal = new CountDownLatch(1);
        decoder.makeRequest(new PeriodRequest((2), decoderResult -> {
            Sample = converterBytesToChannels(decoderResult.bytes, decoder.ChannelsNumber)[0];
            signal.countDown();
        }));
        decoder.start();
        signal.await();
    }

    @Test
    public void FourierFastTransformDefault() {
        ExpectedArray = fft_Default.Transform(Sample);
        Assert.assertFalse(ExpectedArray.length < 10);
    }

    @Test
    public void FourierFastTransformNative() {
        float[] fft = fft_Native.Transform(Sample);
        // float equity = calculateEquity(fft, ExpectedArray);
        // Log.i("equity", "" + equity);
        //Assert.assertTrue(equity < 50);
    }

    @Test
    public void FourierFastTransformAdapted() {
        float[] fft = fft_Adapted.Transform(Sample);
        float equity = calculateEquity(fft, ExpectedArray);
        Log.i("equity", "" + equity);
        Assert.assertTrue(equity < 50);
    }

    @Test
    public void FourierFastTransformPrecise() {
        float[] fft = fft_Precise.Transform(Sample);
        float equity = calculateEquity(fft, ExpectedArray);
        Log.i("equity", "" + equity);
        Assert.assertTrue(equity < 50);
    }
}