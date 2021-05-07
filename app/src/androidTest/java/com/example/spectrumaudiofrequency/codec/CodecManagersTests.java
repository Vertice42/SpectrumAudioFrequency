package com.example.spectrumaudiofrequency.codec;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({EncoderCodecManagerTest.class,
        DecoderCodecManagerTest.class,
        DecoderManagerWithSaveDataTest.class})
public class CodecManagersTests {
}