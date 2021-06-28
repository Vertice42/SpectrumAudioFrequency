package com.example.spectrumaudiofrequency.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class SynchronizedArraylist<E> implements List<E> {
    private final ArrayList<E> arrayList;

    public SynchronizedArraylist() {
        this.arrayList = new ArrayList<>();
    }

    @Override
    public synchronized int size() {
        return arrayList.size();
    }

    @Override
    public boolean isEmpty() {
        return arrayList.isEmpty();
    }

    @Override
    public boolean contains(@Nullable @org.jetbrains.annotations.Nullable Object o) {
        return arrayList.contains(o);
    }

    @NonNull
    @NotNull
    @Override
    public Iterator<E> iterator() {
        return (Iterator<E>) arrayList.iterator();
    }

    @NonNull
    @NotNull
    @Override
    public Object[] toArray() {
        return arrayList.toArray();
    }

    @NonNull
    @NotNull
    @Override
    public <T> T[] toArray(@NonNull @NotNull T[] a) {
        return (T[]) arrayList.toArray();
    }

    @Override
    public synchronized boolean add(E e) {
        return arrayList.add(e);
    }

    @Override
    public synchronized boolean remove(@Nullable @org.jetbrains.annotations.Nullable Object o) {
        return arrayList.remove(o);
    }

    @Override
    public boolean containsAll(@NonNull @NotNull Collection<?> c) {
        return arrayList.contains(c);
    }

    @Override
    public boolean addAll(@NonNull @NotNull Collection<? extends E> c) {
        return arrayList.addAll(c);
    }

    @Override
    public boolean addAll(int index, @NonNull @NotNull Collection<? extends E> c) {
        return arrayList.addAll(c);
    }

    @Override
    public synchronized boolean removeAll(@NonNull @NotNull Collection<?> c) {
        return arrayList.removeAll(c);
    }

    @Override
    public boolean retainAll(@NonNull @NotNull Collection<?> c) {
        return arrayList.removeAll(c);
    }

    @Override
    public synchronized void clear() {
        arrayList.clear();
    }

    @Override
    public synchronized E get(int index) {
        return arrayList.get(index);
    }

    @Override
    public synchronized E set(int index, E element) {
        return arrayList.set(index, element);
    }

    @Override
    public synchronized void add(int index, E element) {
        arrayList.add(index, element);
    }

    @Override
    public synchronized E remove(int index) {
        return arrayList.remove(index);
    }

    @Override
    public int indexOf(@Nullable @org.jetbrains.annotations.Nullable Object o) {
        return arrayList.indexOf(o);
    }

    @Override
    public int lastIndexOf(@Nullable @org.jetbrains.annotations.Nullable Object o) {
        return arrayList.lastIndexOf(o);
    }

    @NonNull
    @NotNull
    @Override
    public ListIterator<E> listIterator() {
        return arrayList.listIterator();
    }

    @NonNull
    @NotNull
    @Override
    public ListIterator<E> listIterator(int index) {
        return arrayList.listIterator(index);
    }

    @NonNull
    @NotNull
    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return arrayList.subList(fromIndex, toIndex);
    }
}

