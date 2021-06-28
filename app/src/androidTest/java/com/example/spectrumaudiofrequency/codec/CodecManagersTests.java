package com.example.spectrumaudiofrequency.codec;

import com.example.spectrumaudiofrequency.R;
import com.example.spectrumaudiofrequency.codec.media_decode_tests.MediaDecoderTest;
import com.example.spectrumaudiofrequency.codec.media_decode_tests.MediaDecoderWithStorageTest;
import com.example.spectrumaudiofrequency.codec.media_encoder_test.MediaEncoderTest;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({MediaEncoderTest.class,
        MediaDecoderTest.class,
        MediaDecoderWithStorageTest.class})
public class CodecManagersTests {
    public final static int SoundID = R.raw.video_input3;
    public final static int TrackIndex = 1;
}