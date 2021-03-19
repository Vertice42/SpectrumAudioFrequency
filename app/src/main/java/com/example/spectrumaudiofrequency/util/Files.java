package com.example.spectrumaudiofrequency.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class Files {
    public static String getFileName(String path){
        return path.substring(path.lastIndexOf("/")+1);
    }

    public static Uri getUriFromResourceId(Context context, int ResourceId){
        return Uri.parse("android.resource://" + context.getPackageName() + "/raw/" + ResourceId);
    }

    public static void SaveJsonFile(Context context, String FileName, String data) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter
                    (context.openFileOutput(FileName + ".json", Context.MODE_PRIVATE));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    public static String ReadJsonFile(Context context, String FileName) {

        String ret = "";

        try {
            InputStream inputStream = context.openFileInput(FileName + ".json");

            if (inputStream != null) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ((receiveString = bufferedReader.readLine()) != null) {
                    stringBuilder.append("\n").append(receiveString);
                }

                inputStream.close();
                ret = stringBuilder.toString();
            }
        } catch (FileNotFoundException e) {
            Log.e("ReadJsonFile", "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e("ReadJsonFile", "Can not read file: " + e.toString());
        }

        return ret;
    }
}
