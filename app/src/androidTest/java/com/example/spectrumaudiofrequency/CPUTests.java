package com.example.spectrumaudiofrequency;
import com.example.spectrumaudiofrequency.util.Files;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Random;

public class CPUTests {
    @Test
    public void getFileName() {
        String path = "/storage/sdcard0/DCIM/Camera/1414240995236.jpg";
        String FileNameExpected = "1414240995236.jpg";

        Assert.assertEquals(Files.getFileName(path), FileNameExpected);
    }

    @Test
    public void add0onList() {
        int length = 20;
        ArrayList<Integer> integers = new ArrayList<>();

        Random random = new Random();


        for (int i = 0; i < length; i++) {
            int new_value = random.nextInt(10);
            int index = 0;
            while (index < integers.size()) {
                if (new_value > integers.get(index)) index++;
                else break;
            }
            integers.add(index, new_value);
        }

        for (int i = 0; i < integers.size(); i++) {
            System.out.println(" index:" + i + " value:" + integers.get(i));
        }
    }
}