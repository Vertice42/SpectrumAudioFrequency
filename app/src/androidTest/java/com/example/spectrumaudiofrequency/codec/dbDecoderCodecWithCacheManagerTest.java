package com.example.spectrumaudiofrequency.codec;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.spectrumaudiofrequency.core.codec_manager.media_decoder.dbDecoderManager;
import com.example.spectrumaudiofrequency.core.codec_manager.media_decoder.dbDecoderManager.MediaSpecs;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

@RunWith(AndroidJUnit4.class)
public class dbDecoderCodecWithCacheManagerTest {
    private final String MediaName = "choose";
    private final dbDecoderManager dbManager;

    public dbDecoderCodecWithCacheManagerTest() {
        Context context = ApplicationProvider.getApplicationContext();
        dbManager = new dbDecoderManager(context, MediaName);
    }

    @Test
    public void addSamplePiece() {
        Assert.assertFalse(dbManager.MediaIsDecoded(MediaName));

        byte[] bytesToTest = new byte[200];
        new Random().nextBytes(bytesToTest);

        int SampleId = 55;

        dbManager.addSamplePiece(SampleId, bytesToTest);
        byte[] dbSamplePiece = dbManager.getSamplePiece(SampleId);
        dbManager.deleteSamplePiece(SampleId);

        Assert.assertArrayEquals(dbSamplePiece, bytesToTest);
    }

    @Test
    public void setDecoded() {
        MediaSpecs mediaSpecs = new MediaSpecs(MediaName, 1800000, 24000);
        dbManager.setDecoded(mediaSpecs);
        Assert.assertTrue(dbManager.MediaIsDecoded(MediaName));
        Assert.assertEquals(dbManager.getMediaSpecs(), mediaSpecs);
    }

    @After
    public void Clear() {
        dbManager.deleteMediaDecoded(MediaName);
        dbManager.close();
    }
}