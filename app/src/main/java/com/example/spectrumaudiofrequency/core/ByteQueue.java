package com.example.spectrumaudiofrequency.core;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;

public class ByteQueue implements Queue<Byte> {
    private final ArrayList<Byte> byteArrayList;

    public ByteQueue() {
        byteArrayList = new ArrayList<Byte>();
    }

    @Override
    public int size() {
        return byteArrayList.size();
    }

    @Override
    public boolean isEmpty() {
        return byteArrayList.size() == 0;
    }

    @Override
    public boolean contains(@Nullable Object o) {
        for (int i = 0; i < byteArrayList.size(); i++) {
            if (Objects.equals(o, byteArrayList.get(i))) return true;
        }
        return false;
    }

    @NonNull
    @Override
    public Iterator iterator() {
        return byteArrayList.iterator();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void forEach(@NonNull Consumer action) {
        for (int i = 0; i < byteArrayList.size(); i++) {
            action.accept(byteArrayList.get(i));
        }
    }

    @NonNull
    @Override
    public Byte[] toArray() {
        return (Byte[]) byteArrayList.toArray();
    }

    @NonNull
    @Override
    public Byte[] toArray(@NonNull Object[] a) {
        return (Byte[]) byteArrayList.toArray(a);
    }

    @Override
    public boolean add(Byte o) {
        return byteArrayList.add((Byte) o);
    }

    public boolean add(byte[] bytes) {
        boolean add = false;
        for (byte aByte : bytes) add = byteArrayList.add(aByte);
        return add;
    }

    @Override
    public boolean remove(@Nullable Object o) {
        return byteArrayList.remove(o);
    }

    @Override
    public boolean addAll(@NonNull Collection c) {
        return byteArrayList.addAll(c);
    }

    @Override
    public void clear() {
        byteArrayList.clear();
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
        return byteArrayList.containsAll(c);
    }

    @Override
    public boolean offer(Byte o) {
        return byteArrayList.add((Byte) o);
    }

    @Override
    public Byte remove() {
        return byteArrayList.remove(0);
    }

    @Nullable
    @Override
    public Byte poll() {
        Byte aByte = byteArrayList.get(byteArrayList.size());
        byteArrayList.remove(aByte);
        return aByte;
    }

    @Override
    public Byte element() {
        return byteArrayList.get(0);
    }

    @Nullable
    @Override
    public Byte peek() {
        Byte aByte = byteArrayList.get(0);
        byteArrayList.remove(aByte);
        return aByte;
    }

    public byte[] peekList(int length) {
        if (length > this.size())length = this.size();
        byte[] bytes = new byte[length];

        for (int i = 0; i < length; i++) {
            Byte peek = peek();
            if (peek != null) bytes[i] = peek;
        }
        return bytes;
    }
}
