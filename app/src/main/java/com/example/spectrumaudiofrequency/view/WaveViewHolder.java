package com.example.spectrumaudiofrequency.view;

import android.graphics.Bitmap;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

public class WaveViewHolder extends RecyclerView.ViewHolder {
    public Bitmap ImageBitmap;
    ImageView imageView;

    public WaveViewHolder(ImageView v,int Width,int Height) {
        super(v);
        imageView = v;
        ImageBitmap = Bitmap.createBitmap(Width, Height, Bitmap.Config.ARGB_8888);
    }

    public void updateImage(Bitmap ImageBitmap) {
        this.ImageBitmap = ImageBitmap;
        imageView.post(() -> imageView.setImageBitmap(ImageBitmap));
    }
}
