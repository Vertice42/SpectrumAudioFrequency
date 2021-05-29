package com.example.spectrumaudiofrequency.core.codec_manager;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;

interface QueueElement {
    long getIndex();
}

public class SortedQueue implements Queue<QueueElement> {
    private final LinkedList<QueueElement> queueElements;

    public SortedQueue() {
        queueElements = new LinkedList<>();
    }

    @Override
    public int size() {
        return queueElements.size();
    }

    @Override
    public boolean isEmpty() {
        return queueElements.size() == 0;
    }

    @Override
    public boolean contains(@Nullable Object o) {
        for (int i = 0; i < queueElements.size(); i++) {
            if (Objects.equals(o, queueElements.get(i))) return true;
        }
        return false;
    }

    @NonNull
    @Override
    public Iterator<QueueElement> iterator() {
        return queueElements.iterator();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void forEach(@NonNull Consumer consumer) {
        for (int i = 0; i < queueElements.size(); i++)
            consumer.accept(queueElements.get(i));
    }

    @NonNull
    @Override
    public QueueElement[] toArray() {
        return (QueueElement[]) queueElements.toArray();
    }

    @Override
    public QueueElement[] toArray(@NonNull Object[] a) {
        return (QueueElement[]) queueElements.toArray(a);
    }

    @Override
    public boolean add(QueueElement queueElement) {
        int i = 0;
        while (i < queueElements.size()) {
            if (queueElement.getIndex() > queueElements.get(i).getIndex()) i++;
            else break;
        }
        queueElements.add(i, (QueueElement) queueElement);
        return true;
    }

    @Override
    public boolean remove(@Nullable Object o) {
        return queueElements.remove(o);
    }

    @Override
    public boolean addAll(@NonNull Collection c) {
        return queueElements.addAll(c);
    }

    @Override
    public void clear() {
        queueElements.clear();
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
        return queueElements.containsAll(c);
    }

    @Override
    public boolean offer(QueueElement o) {
        return queueElements.add((QueueElement) o);
    }

    @Override
    public QueueElement remove() {
        return queueElements.remove(0);
    }

    @Override
    public QueueElement poll() {
        return queueElements.poll();
    }

    public QueueElement pollFirst() {
        return queueElements.pollFirst();
    }

    @Override
    public QueueElement element() {
        return queueElements.get(0);
    }

    public QueueElement get(int index) {
        return queueElements.get(index);
    }

    @Nullable
    @Override
    public QueueElement peek() {
        QueueElement codecSample = queueElements.get(0);
        queueElements.remove(codecSample);
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
        return queueElements.toString();
    }
}
