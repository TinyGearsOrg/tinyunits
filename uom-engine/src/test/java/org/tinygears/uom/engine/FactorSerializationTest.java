/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The serialization proxy of {@link Factor}: forged streams are rejected, valid ones normalised. */
class FactorSerializationTest {

    static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        return bytes.toByteArray();
    }

    static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T roundTrip(T o) throws Exception {
        return (T) deserialize(serialize(o));
    }

    private static InvalidObjectException assertInvalid(String message, Object forged) {
        InvalidObjectException e = assertThrows(InvalidObjectException.class, () -> deserialize(serialize(forged)));
        assertEquals(message, e.getMessage());
        return e;
    }

    @Test
    void forgedFactors() throws Exception {
        BigInteger one = BigInteger.ONE;
        InvalidObjectException e = assertInvalid(
                "invalid factor: division by zero",
                new Factor.Proxy(one, BigInteger.ZERO, 0, 1));
        assertTrue(e.getCause() instanceof ArithmeticException, e.getCause().toString());
        assertInvalid("invalid factor: root index must be positive: -1", new Factor.Proxy(one, one, 0, -1));
        assertInvalid("invalid factor: root index must be positive: 0", new Factor.Proxy(one, one, 0, 0));
        assertInvalid("invalid factor: null numerator", new Factor.Proxy(null, one, 0, 1));
        assertInvalid("invalid factor: null denominator", new Factor.Proxy(one, null, 0, 1));
        // Non-normalised parts are normalised on read.
        assertEquals(Factor.of(1, 2), roundTrip(new Factor.Proxy(BigInteger.TWO, BigInteger.valueOf(4), 0, 1)));
        assertEquals(
                Factor.of(-3, 2),
                roundTrip(new Factor.Proxy(BigInteger.valueOf(6), BigInteger.valueOf(-4), 0, 1)));
        assertEquals(Factor.ofLong(2), roundTrip(new Factor.Proxy(BigInteger.valueOf(4), one, 0, 2)));
        assertEquals(Factor.PI, roundTrip(new Factor.Proxy(one, one, 2, 2)));
    }

    @Test
    void forgedHugeNumeratorIsRejectedQuickly() {
        // Two random coprime-looking 4M-bit values: reducing them by gcd first would take minutes.
        BigInteger n = new BigInteger(4_000_000, new Random(1));
        BigInteger d = new BigInteger(4_000_000, new Random(2));
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            assertInvalid("invalid factor: factor too large", new Factor.Proxy(n, d, 0, 1));
            assertInvalid("invalid factor: factor too large", new Factor.Proxy(n, BigInteger.ONE, 0, 1));
            assertInvalid("invalid factor: factor too large", new Factor.Proxy(BigInteger.ONE, d, 0, 1));
        });
    }

    @Test
    void factorByteCapIsExact() throws Exception {
        // 8193 bytes hold up to 65_543 bits with the sign; 65_544 bits need 8194 and are refused before allocation.
        BigInteger max = BigInteger.ONE.shiftLeft(65_536).subtract(BigInteger.ONE);
        assertEquals(8193, max.toByteArray().length);
        assertEquals(max, ((Factor) deserialize(serialize(new Factor.Proxy(max, BigInteger.TWO, 0, 1)))).numerator());
        BigInteger widest = BigInteger.ONE.shiftLeft(65_542);
        assertEquals(8193, widest.toByteArray().length);
        InvalidObjectException e = assertInvalid(
                "invalid factor: factor too large",
                new Factor.Proxy(widest, BigInteger.ONE, 0, 1));
        assertTrue(e.getCause() instanceof ArithmeticException, "rejected by the bit check after reading");
        BigInteger over = BigInteger.ONE.shiftLeft(65_543);
        assertEquals(8194, over.toByteArray().length);
        e = assertInvalid("invalid factor: factor too large", new Factor.Proxy(over, BigInteger.ONE, 0, 1));
        assertNull(e.getCause(), "rejected by the byte cap before reading");
    }
}
