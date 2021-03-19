package com.example.spectrumaudiofrequency;

import com.example.spectrumaudiofrequency.util.Files;

import org.junit.Assert;
import org.junit.Test;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class CPUTests {
    @Test
    public void getFileName() {
        String path = "/storage/sdcard0/DCIM/Camera/1414240995236.jpg";
        String FileNameExpected = "1414240995236.jpg";

        Assert.assertEquals(Files.getFileName(path), FileNameExpected);
    }
}