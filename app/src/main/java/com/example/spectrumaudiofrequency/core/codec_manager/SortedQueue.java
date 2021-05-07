package com.example.spectrumaudiofrequency.core.codec_manager;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;

interface QueueElement {
    public long getIndex();
}

class SortedQueue implements Queue<QueueElement> {
    private final ArrayList<QueueElement> codecSampleArrayList;

    public SortedQueue() {
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
    public Iterator<QueueElement> iterator() {
        return codecSampleArrayList.iterator();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void forEach(@NonNull Consumer consumer) {
        for (int i = 0; i < codecSampleArrayList.size(); i++)
            consumer.accept(codecSampleArrayList.get(i));
    }

    @NonNull
    @Override
    public QueueElement[] toArray() {
        return (QueueElement[]) codecSampleArrayList.toArray();
    }

    @Override
    public QueueElement[] toArray(@NonNull Object[] a) {
        return (QueueElement[]) codecSampleArrayList.toArray(a);
    }

    @Override
    public boolean add(QueueElement queueElement) {
        int i = 0;
        while (i < codecSampleArrayList.size()) {
            if (queueElement.getIndex() > codecSampleArrayList.get(i).getIndex()) i++;
            else break;
        }
        codecSampleArrayList.add(i, (QueueElement) queueElement);
        return true;
    }

    public boolean add(Object[] codecSamples) {
        boolean add = false;
        for (Object codecSample : codecSamples)
            add = codecSampleArrayList.add((QueueElement) codecSample);
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
    public boolean offer(QueueElement o) {
        return codecSampleArrayList.add((QueueElement) o);
    }

    @Override
    public QueueElement remove() {
        return codecSampleArrayList.remove(0);
    }

    @Nullable
    @Override
    public QueueElement poll() {
        QueueElement QueueElement = codecSampleArrayList.get(codecSampleArrayList.size() - 1);
        codecSampleArrayList.remove(QueueElement);
        return QueueElement;
    }

    @Override
    public QueueElement element() {
        return codecSampleArrayList.get(0);
    }

    public QueueElement get(int index) {
        return codecSampleArrayList.get(index);
    }

    @Nullable
    @Override
    public QueueElement peek() {
        QueueElement codecSample = codecSampleArrayList.get(0);
        codecSampleArrayList.remove(codecSample);
        return codecSample;
    }

    public QueueElement[] peekList(int length) {
        if (length > this.size()) length = this.size();
        QueueElement[] codecSample = new QueueElement[length];

        for (int i = 0; i < length; i++) {
            QueueElement peek = peek();
            if (peek != null) codecSample[i] = peek;
        }
        return codecSample;
    }

    @Override
    public @NotNull String toString() {
        return codecSampleArrayList.toString();
    }
}
