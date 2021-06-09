package com.example.spectrumaudiofrequency.util;

import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VerifyTimeOut {
    private final long MaxTime;
    private final CountDownLatch countDownLatch;
    private final ExecutorService executorService;
    private final Class<?> ClassInSpec;
    private final boolean Enable;
    private boolean TimeOutPass;

    public VerifyTimeOut(Class<?> ClassInSpec, long MaxTime, CountDownLatch countDownLatch) {
        this.ClassInSpec = ClassInSpec;
        this.MaxTime = MaxTime;
        this.countDownLatch = countDownLatch;
        this.executorService = Executors.newSingleThreadExecutor();
        this.Enable = true;
        CountTimeout();
    }

    public VerifyTimeOut(Class<?> ClassInSpec, long MaxTime, CountDownLatch countDownLatch, boolean Enable) {
        this.ClassInSpec = ClassInSpec;
        this.MaxTime = MaxTime;
        this.countDownLatch = countDownLatch;
        this.executorService = Executors.newSingleThreadExecutor();
        this.Enable = Enable;
        CountTimeout();
    }

    private void CountTimeout() {
        executorService.execute(() -> {
            try {
                Thread.sleep(MaxTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (TimeOutPass) CountTimeout();
            else {
                Log.e(ClassInSpec.getCanonicalName(), "TimeOut");
                if (Enable) countDownLatch.countDown();
            }
            TimeOutPass = false;
        });
    }

    public void Pass() {
        TimeOutPass = true;
    }

}
