package com.github.thatapplepieguy.packetpatcher.util;

import io.netty.util.concurrent.FastThreadLocal;

import java.util.function.Supplier;

public class FastThreadLocals {

    public static <T> FastThreadLocal<T> withInitial(Supplier<? extends T> supplier) {
        return new FastThreadLocal<>() {
            @Override
            protected T initialValue() {
                return supplier.get();
            }
        };
    }
}
