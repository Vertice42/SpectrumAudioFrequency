package com.example.spectrumaudiofrequency;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.spectrumaudiofrequency.MediaDecoder.dbAudioDecoderManager;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

@RunWith(AndroidJUnit4.class)
public class dbAudioDecoderTest {
    private final String MediaName = "choose";
    private final dbAudioDecoderManager dbManager;

    public dbAudioDecoderTest() {
        Context context = ApplicationProvider.getApplicationContext();
        dbManager = new dbAudioDecoderManager(context,MediaName);
    }

    @Test
    public void addSamplePiece() {
        Assert.assertFalse(dbManager.MediaIsDecoded(MediaName));

        byte[] bytesToTest = new byte[200];
        new Random().nextBytes(bytesToTest);

        int SamplePeace = 55;

        dbManager.addSamplePiece(SamplePeace, bytesToTest);
        byte[] dbSamplePiece = dbManager.getSamplePiece(SamplePeace);
        dbManager.deleteSamplePiece(SamplePeace);
        dbManager.close();

        Assert.assertArrayEquals(dbSamplePiece, bytesToTest);
    }

    @Test
    public void setDecoded() {
        dbManager.setDecoded(MediaName);
        Assert.assertTrue(dbManager.MediaIsDecoded(MediaName));
    }
}