package com.example.spectrumaudiofrequency;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.spectrumaudiofrequency.core.VideoMuxer;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.example.spectrumaudiofrequency.util.Files.getUriFromResourceId;

@RunWith(AndroidJUnit4.class)
public class VideoMuxerTest {

    @Test
    public void MainTest() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        VideoMuxer videoMuxer = new VideoMuxer(context,
                getUriFromResourceId(context, R.raw.video_input1),
                getUriFromResourceId(context, R.raw.simcity1));
        boolean end = videoMuxer.encode();
    }
}
