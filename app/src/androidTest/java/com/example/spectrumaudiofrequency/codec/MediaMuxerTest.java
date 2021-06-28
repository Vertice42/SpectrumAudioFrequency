package com.example.spectrumaudiofrequency.codec;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.R;
import com.example.spectrumaudiofrequency.core.MediaMuxerManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

@RunWith(AndroidJUnit4.class)
public class MediaMuxerTest {
    private static final int VideoId = R.raw.video_input3;
    private static final int[] IdsOfSounds = {R.raw.game_description, R.raw.hollow};

    @Test
    public void Mux() throws InterruptedException {
        final CountDownLatch EndSignal = new CountDownLatch(1);

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        String resourceName = context.getResources().getResourceEntryName(VideoId);
        MediaMuxerManager mediaMuxerManager = new MediaMuxerManager(context,
                resourceName, getUriFromResourceId(context, VideoId), IdsOfSounds);

        MediaMuxerManager.Cutoff[] cutoffs = new MediaMuxerManager.Cutoff[5];
        long cutTime = mediaMuxerManager.getVideoDuration() / cutoffs.length;
        long time = cutTime;
        for (int i = 0; i < cutoffs.length; i++) {
            cutoffs[i] = new MediaMuxerManager.Cutoff(time, time + 23220 * 10);
            time += cutTime;
        }

        mediaMuxerManager.setFinishListener(EndSignal::countDown);
        mediaMuxerManager.start(cutoffs);

        EndSignal.await();
    }
}