package com.jinkeen.lifeplus.log.parser;

public class Tuple<K, V> {
    public K first;

    public V second;

    public static <K, V> Tuple<K, V> create(K first, V second) {
        Tuple<K, V> tuple = new Tuple<>();
        tuple.first = first;
        tuple.second = second;
        return tuple;
    }
}
