package com.example.spectrumaudiofrequency.codec.media_decode_tests;

import android.util.Log;

import com.example.spectrumaudiofrequency.codec.CodecTestResult;

import java.util.LinkedList;

import static org.junit.Assert.fail;

public class CodecErrorChecker {
    public static void check(String ClassTestName, LinkedList<CodecTestResult> DecodeResults) {
        StringBuilder ResultList = new StringBuilder();
        StringBuilder ErrorsList = new StringBuilder();
        boolean HasError = false;
        long previusSampleTime = -1;
        for (int i = 0; i < DecodeResults.size(); i++) {
            CodecTestResult codecTestResult = DecodeResults.get(i);
            if (codecTestResult != null) {
                if (codecTestResult.SampleTime == previusSampleTime) {
                    HasError = true;
                    ResultList.append("{{").append(codecTestResult.SampleTime).append("}}");
                    ErrorsList.append("[REPEATED_SAMPLE ")
                            .append(codecTestResult.SampleTime)
                            .append(']');
                } else if (codecTestResult.SampleTime < previusSampleTime) {
                    HasError = true;
                    ResultList.append("{{").append(codecTestResult.SampleTime).append("}}");
                    ErrorsList.append("[INCORRECT_ORDER ")
                            .append(codecTestResult.SampleTime)
                            .append(']');
                } else {
                    ResultList.append('[').append(codecTestResult.SampleTime).append(']');
                }
                previusSampleTime = codecTestResult.SampleTime;
            }
        }
        if (HasError) {
            Log.e("ErrorsList" + ClassTestName, ErrorsList.toString());
            Log.e("ResultList" + ClassTestName, ResultList.toString());
            fail();
        }
    }
}
