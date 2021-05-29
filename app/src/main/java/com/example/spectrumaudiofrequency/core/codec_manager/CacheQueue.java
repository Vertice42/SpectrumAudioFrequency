package com.example.spectrumaudiofrequency.core.codec_manager;

import java.util.HashMap;

public class CacheQueue<I, E> extends HashMap<I, E> {
    public E get(int id) {
        E e = super.get(id);
        super.remove(id);
        return e;
    }
}
