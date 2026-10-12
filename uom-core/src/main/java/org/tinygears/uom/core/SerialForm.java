// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.function.IntFunction;

/**
 * Stream helpers for the serialization proxies. Objects are written unshared, so the bytes depend on values only,
 * never on object identity; lengths are read before anything is allocated and are bounded.
 */
final class SerialForm {

    /** Bound on atoms per unit or definition and on conversion steps. */
    static final int MAX_ELEMENTS = 1000;

    private SerialForm() {
    }

    static void writeObjects(ObjectOutputStream out, Object[] a) throws IOException {
        out.writeInt(a == null ? -1 : a.length);
        if (a != null) {
            for (Object o : a) {
                out.writeUnshared(o);
            }
        }
    }

    static <T> T[] readObjects(ObjectInputStream in, Class<T> type, IntFunction<T[]> array, String tooMany)
            throws IOException, ClassNotFoundException {
        int n = readLength(in, MAX_ELEMENTS, tooMany);
        if (n < 0) {
            return null;
        }
        T[] a = array.apply(n);
        for (int i = 0; i < n; i++) {
            a[i] = read(in, type);
        }
        return a;
    }

    static void writeInts(ObjectOutputStream out, int[] a) throws IOException {
        out.writeInt(a == null ? -1 : a.length);
        if (a != null) {
            for (int v : a) {
                out.writeInt(v);
            }
        }
    }

    static int[] readInts(ObjectInputStream in, String tooMany) throws IOException {
        int n = readLength(in, MAX_ELEMENTS, tooMany);
        if (n < 0) {
            return null;
        }
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = in.readInt();
        }
        return a;
    }

    static void writeBytes(ObjectOutputStream out, byte[] b) throws IOException {
        out.writeInt(b == null ? -1 : b.length);
        if (b != null) {
            out.write(b);
        }
    }

    static byte[] readBytes(ObjectInputStream in, int max, String tooMany) throws IOException {
        int n = readLength(in, max, tooMany);
        if (n < 0) {
            return null;
        }
        byte[] b = new byte[n];
        in.readFully(b);
        return b;
    }

    /** A length, or -1 for null. */
    private static int readLength(ObjectInputStream in, int max, String tooMany) throws IOException {
        int n = in.readInt();
        if (n < -1) {
            throw new InvalidObjectException("negative length " + n);
        }
        if (n > max) {
            throw new InvalidObjectException(tooMany);
        }
        return n;
    }

    static <T> T read(ObjectInputStream in, Class<T> type) throws IOException, ClassNotFoundException {
        Object o = in.readObject();
        if (o != null && !type.isInstance(o)) {
            throw new InvalidObjectException("expected " + type.getSimpleName() + ", got " + o.getClass().getName());
        }
        return type.cast(o);
    }
}
