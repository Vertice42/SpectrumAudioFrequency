package com.example.spectrumaudiofrequency.core.codec_manager;

import android.media.MediaCodec;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.example.spectrumaudiofrequency.core.codec_manager.MediaFormatConverter.CodecSample;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;

class SampleQueue implements Queue<CodecSample> {
    private final ArrayList<CodecSample> codecSampleArrayList;

    public SampleQueue() {
        codecSampleArrayList = new ArrayList<>();
    }

    @Override
    public int size() {
        return codecSampleArrayList.size();
    }

    @Override
    public boolean isEmpty() {
        return codecSampleArrayList.size() == 0;
    }

    @Override
    public boolean contains(@Nullable Object o) {
        for (int i = 0; i < codecSampleArrayList.size(); i++) {
            if (Objects.equals(o, codecSampleArrayList.get(i))) return true;
        }
        return false;
    }

    @NonNull
    @Override
    public Iterator iterator() {
        return codecSampleArrayList.iterator();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void forEach(@NonNull Consumer action) {
        for (int i = 0; i < codecSampleArrayList.size(); i++) {
            action.accept(codecSampleArrayList.get(i));
        }
    }

    @NonNull
    @Override
    public CodecSample[] toArray() {
        return (MediaFormatConverter.CodecSample[]) codecSampleArrayList.toArray();
    }

    @NonNull
    @Override
    public MediaFormatConverter.CodecSample[] toArray(@NonNull Object[] a) {
        return (MediaFormatConverter.CodecSample[]) codecSampleArrayList.toArray(a);
    }

    boolean isBigger(MediaCodec.BufferInfo a, MediaCodec.BufferInfo b) {
        if (a.presentationTimeUs > b.presentationTimeUs) return true;
        else return a.presentationTimeUs == b.presentationTimeUs && a.size < b.size;
    }

    @Override
    public boolean add(CodecSample codecSample) {
        int index = 0;
        while (index < codecSampleArrayList.size()) {
            if (isBigger(codecSample.bufferInfo, codecSampleArrayList.get(index).bufferInfo)) {
                index++;
            } else break;
        }
        codecSampleArrayList.add(index, codecSample);
        return true;
    }

    public boolean add(MediaFormatConverter.CodecSample[] codecSamples) {
        boolean add = false;
        for (MediaFormatConverter.CodecSample codecSample : codecSamples)
            add = codecSampleArrayList.add(codecSample);
        return add;
    }

    @Override
    public boolean remove(@Nullable Object o) {
        return codecSampleArrayList.remove(o);
    }

    @Override
    public boolean addAll(@NonNull Collection c) {
        return codecSampleArrayList.addAll(c);
    }

    @Override
    public void clear() {
        codecSampleArrayList.clear();
    }

    @Override
    public boolean retainAll(@NonNull Collection c) {
        return c.retainAll(c);
    }

    @Override
    public boolean removeAll(@NonNull Collection c) {
        return c.removeAll(c);
    }

    @Override
    public boolean containsAll(@NonNull Collection c) {
        return codecSampleArrayList.containsAll(c);
    }

    @Override
    public boolean offer(CodecSample o) {
        return codecSampleArrayList.add((MediaFormatConverter.CodecSample) o);
    }

    @Override
    public CodecSample remove() {
        return codecSampleArrayList.remove(0);
    }

    @Nullable
    @Override
    public CodecSample poll() {
        MediaFormatConverter.CodecSample CodecSample = codecSampleArrayList.get(codecSampleArrayList.size() - 1);
        codecSampleArrayList.remove(CodecSample);
        return CodecSample;
    }

    @Override
    public MediaFormatConverter.CodecSample element() {
        return codecSampleArrayList.get(0);
    }

    public MediaFormatConverter.CodecSample get(int index) {
        return codecSampleArrayList.get(index);
    }

    @Nullable
    @Override
    public MediaFormatConverter.CodecSample peek() {
        CodecSample codecSample = codecSampleArrayList.get(0);
        codecSampleArrayList.remove(codecSample);
        return codecSample;
    }

    public MediaFormatConverter.CodecSample[] peekList(int length) {
        if (length > this.size()) length = this.size();
        CodecSample[] codecSample = new MediaFormatConverter.CodecSample[length];

        for (int i = 0; i < length; i++) {
            CodecSample peek = peek();
            if (peek != null) codecSample[i] = peek;
        }
        return codecSample;
    }

}
