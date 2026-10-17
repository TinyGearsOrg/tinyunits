/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.quantity.Length;
import org.tinygears.uom.quantity.Temperature;
import org.tinygears.uom.systems.SI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Units and quantities round-trip exactly, and a forged stream is refused. */
class NativeSerializationTest {

    private static byte[] write(Object o) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        return bytes.toByteArray();
    }

    private static Object read(byte[] b) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(b))) {
            return in.readObject();
        }
    }

    private static Object roundTrip(Object o) throws IOException, ClassNotFoundException {
        return read(write(o));
    }

    @Test
    void quantitiesAndUnitsRoundTrip() throws Exception {
        List<Quantity<?>> samples = List.of(
                Length.of(1.5, SI.KILOMETRE),
                Length.of(-0.0, SI.METRE),
                Length.of(Double.NaN, SI.METRE),
                Length.of(new BigDecimal("0.10"), SI.KILOMETRE, Scale.ABSOLUTE, new MathContext(7, RoundingMode.DOWN)),
                Temperature.of(5, SI.DEGREE_CELSIUS, Scale.RELATIVE),
                Untyped.of(new BigDecimal("6"), SI.KILOGRAM.multiply(SI.METRE), MathContext.UNLIMITED));
        for (Quantity<?> q : samples) {
            Quantity<?> back = (Quantity<?>) roundTrip(q);
            assertEquals(q, back);
            assertEquals(q.getClass(), back.getClass());
            assertEquals(q.value().toString(), back.value().toString());
            assertEquals(q.mathContext(), back.mathContext());
            assertEquals(q.unit().symbol(), back.unit().symbol());
            assertEquals(q.unit().type(), back.unit().type());
        }
        Unit<?> km = (Unit<?>) roundTrip(SI.KILOMETRE);
        assertEquals(SI.KILOMETRE, km);
        assertSame(Length.class, km.type());
        assertEquals("kilometre", km.name());
    }

    @Test
    void forgedStreamsAreRefused() throws Exception {
        byte[] length = write(Length.of(1, SI.METRE));
        assertEquals(
                "invalid unit: m (L) is not a unit of Volume",
                assertThrows(InvalidObjectException.class, () -> read(replace(length, "Length", "Volume")))
                        .getMessage());
        assertEquals(
                "invalid unit: unknown type org.tinygears.uom.quantity.Lengtx",
                assertThrows(InvalidObjectException.class, () -> read(replace(length, "Length", "Lengtx")))
                        .getMessage());
        assertEquals(
                "invalid quantity: unknown scale XBSOLUTE",
                assertThrows(InvalidObjectException.class, () -> read(replace(length, "ABSOLUTE", "XBSOLUTE")))
                        .getMessage());
        assertEquals(Scale.RELATIVE, ((Quantity<?>) read(replace(length, "ABSOLUTE", "RELATIVE"))).scale());
    }

    /** {@code b} with every occurrence of the ASCII text {@code from} replaced by {@code to}, of the same length. */
    private static byte[] replace(byte[] b, String from, String to) {
        byte[] f = from.getBytes(StandardCharsets.US_ASCII);
        byte[] t = to.getBytes(StandardCharsets.US_ASCII);
        byte[] out = b.clone();
        int n = 0;
        for (int i = 0; i + f.length <= out.length; i++) {
            if (java.util.Arrays.equals(out, i, i + f.length, f, 0, f.length)) {
                System.arraycopy(t, 0, out, i, t.length);
                n++;
            }
        }
        if (n == 0) {
            throw new AssertionError(from + " not in the stream");
        }
        return out;
    }
}
