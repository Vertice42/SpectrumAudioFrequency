package com.example.spectrumaudiofrequency;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.core.VideoMuxer;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;

import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

@RunWith(AndroidJUnit4.class)
public class VideoMuxerTest {

    @Test
    public void MainTest() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        VideoMuxer videoMuxer = new VideoMuxer(context,
                getUriFromResourceId(context, R.raw.video_input4),
                getUriFromResourceId(context, R.raw.simcity1));

        VideoMuxer.Cutoff[] cutoffs = new VideoMuxer.Cutoff[5];

        long cutTime = videoMuxer.getVideoDuration() / cutoffs.length;
        long time = cutTime;

        for (int i = 0; i < cutoffs.length; i++) {
            cutoffs[i] = new VideoMuxer.Cutoff(time, time + 23220 * 10);
            time += cutTime;
        }

        videoMuxer.prepare(cutoffs);

        Log.i("cutTime", Arrays.toString(cutoffs));

        videoMuxer.render();
    }
}
