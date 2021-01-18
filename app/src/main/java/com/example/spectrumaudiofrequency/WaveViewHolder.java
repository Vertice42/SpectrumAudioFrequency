package com.example.spectrumaudiofrequency;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Date;

public class WaveViewHolder extends RecyclerView.ViewHolder {
    Bitmap ImageBitmap;
    ImageView imageView;

    public WaveViewHolder(ImageView v,int Width,int Height) {
        super(v);
        imageView = v;
        ImageBitmap = Bitmap.createBitmap(Width, Height, Bitmap.Config.ARGB_8888);
    }

    void updateImage() {
        imageView.post(() -> imageView.setImageBitmap(ImageBitmap));
    }
}
