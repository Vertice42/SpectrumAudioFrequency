package com.example.spectrumaudiofrequency;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class UtilTests {
    @Test
    public void getFileName() {
        String path = "/storage/sdcard0/DCIM/Camera/1414240995236.jpg";
        String FileNameExpected = "1414240995236.jpg";

        Assert.assertEquals(Util.getFileName(path), FileNameExpected);
    }
}