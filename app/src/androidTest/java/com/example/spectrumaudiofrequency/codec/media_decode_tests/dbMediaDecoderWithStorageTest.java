package com.example.spectrumaudiofrequency.codec.media_decode_tests;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.spectrumaudiofrequency.R;
import com.example.spectrumaudiofrequency.core.codec_manager.CodecManager;
import com.example.spectrumaudiofrequency.core.codec_manager.MediaDecoder;
import com.example.spectrumaudiofrequency.core.codec_manager.dbDecoderManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

@RunWith(AndroidJUnit4.class)
public class dbMediaDecoderWithStorageTest {
    private final dbDecoderManager dbManager;
    int id = R.raw.hollow;

    public dbMediaDecoderWithStorageTest() {
        Context context = ApplicationProvider.getApplicationContext();
        MediaDecoder decoderToTest = new MediaDecoder(context, id);
        dbManager = new dbDecoderManager(context, decoderToTest);
    }

    @Test
    public void addSample() {
        Assert.assertFalse(dbManager.MediaIsDecoded());

        byte[] bytesToTest = new byte[200];
        new Random().nextBytes(bytesToTest);

        int SampleId = 55;

        dbManager.add(SampleId, bytesToTest);
        byte[] dbSamplePiece = dbManager.getSamplePiece(SampleId);
        dbManager.deleteSamplePiece(SampleId);

        Assert.assertArrayEquals(dbSamplePiece, bytesToTest);
    }

    @Test
    public void addSamples() {
        Assert.assertFalse(dbManager.MediaIsDecoded());

        int SamplesToAdd = 22;
        byte[] bytesToTest = new byte[200];
        new Random().nextBytes(bytesToTest);

        for (int i = 0; i < SamplesToAdd; i++) dbManager.add(i, bytesToTest);

        for (int i = 0; i < SamplesToAdd; i++) {
            CodecManager.CodecSample codecSample = dbManager.getCodecSample(i);
            if (i != SamplesToAdd - 1)
                Assert.assertArrayEquals(bytesToTest, codecSample.bytes);
        }

        Assert.assertEquals(dbManager.getNumberOfSamples(), SamplesToAdd);
    }

    @After
    public void Clear() {
        dbManager.clear();
        dbManager.close();
    }
}