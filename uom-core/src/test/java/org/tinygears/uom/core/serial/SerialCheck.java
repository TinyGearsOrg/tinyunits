/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.core.serial;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.measure.UnitConverter;

import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.Converters;
import org.tinygears.uom.core.Factor;
import org.tinygears.uom.core.UnitAtom;
import org.tinygears.uom.core.UomConverter;
import org.tinygears.uom.core.UomUnit;

/** Subprocess check: reads golden units in a fresh JVM, then round trips in that runtime. Arg: golden dir. */
public final class SerialCheck {

    private SerialCheck() {
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        UomUnit<?> hz = (UomUnit<?>) read(Files.readAllBytes(dir.resolve("hertz.ser")));
        UomUnit<?> degree = (UomUnit<?>) read(Files.readAllBytes(dir.resolve("degree.ser")));
        check("Hz".equals(hz.getSymbol()) && "hertz".equals(hz.getName()), "hertz labels: " + hz);
        check("°".equals(degree.getSymbol()) && "degree".equals(degree.getName()), "degree labels: " + degree);
        // Reading does not register: rad can still be bound to another unit, after which degree.ser conflicts.
        BaseUnits.METRE.alternate("rad");
        try {
            read(Files.readAllBytes(dir.resolve("degree.ser")));
            throw new AssertionError("degree read although rad is bound to metre");
        } catch (InvalidObjectException expected) {
            check(
                    expected.getMessage()
                            .equals("invalid unit atom: symbol rad is already associated to a different unit"),
                    expected.getMessage());
        }
        // A later registration with the same definition gives an equal unit, and reading then resolves to it.
        UomUnit<?> registered = BaseUnits.ONE.divide(BaseUnits.SECOND).alternate("Hz");
        check(hz.equals(registered), "hertz differs from Hz");
        check(
                registered.atoms().keySet().iterator()
                        .next() == ((UomUnit<?>) read(Files.readAllBytes(dir.resolve("hertz.ser")))).atoms().keySet()
                                .iterator().next(),
                "hertz does not resolve to the registered atom");

        UomUnit<?> celsius = BaseUnits.KELVIN.shift(273.15);
        check(celsius.equals(read(write(celsius))), "offset unit round trip");
        UomConverter a = Converters.scale(Factor.ofLong(3));
        UomConverter b = Converters.shift(Factor.ofLong(5));
        List<? extends UnitConverter> steps = a.concatenate(b).getConversionSteps();
        check(steps.equals(((UomConverter) read(write(a.concatenate(b)))).getConversionSteps()), "step order");
        List<String> symbols = hz.atoms().keySet().stream().map(UnitAtom::symbol).toList();
        System.out.println("ok named=" + UomUnit.class.getModule().isNamed() + " atoms=" + symbols);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static byte[] write(Object o) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        return bytes.toByteArray();
    }

    private static Object read(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }
}
