package com.example.spectrumaudiofrequency;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.MediaDecoder.dbAudioDecoder;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Random;

@RunWith(AndroidJUnit4.class)
public class dbAudioDecoderTest {
    private final dbAudioDecoder db;

    public dbAudioDecoderTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        this.db = new dbAudioDecoder(context);
    }

    @Test
    public void dbAudioDecoder() {
        byte[] bytesToTest = new byte[200];
        new Random().nextBytes(bytesToTest);

        db.addSamplePiece(0, bytesToTest);
        byte[] dbSamplePiece = db.getSamplePiece(0);
        db.deleteSamplePiece(0);
        db.close();

        Assert.assertArrayEquals(dbSamplePiece, bytesToTest);

    }
}
