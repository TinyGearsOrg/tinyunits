/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.jsr.systems.initorder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.tinygears.uom.jsr.BaseUnits;
import org.tinygears.uom.jsr.quantity.LengthFactory;
import org.tinygears.uom.jsr.quantity.QuantityTypes;
import org.tinygears.uom.jsr.systems.SI;

/** Run by {@code InitOrderTest} in a fresh JVM: initialises {@code args[0]} first, then every other class. */
public final class FirstTouch {
    public static final List<String> CATALOGS = List
            .of("SI", "USCustomary", "Imperial", "CGS", "ESU", "EMU", "Gaussian", "UnitSystems", "UomServiceProvider");

    private FirstTouch() {
    }

    public static void main(String[] args) throws Exception {
        // Nothing of the library may be touched before this line.
        Class.forName(args[0], true, FirstTouch.class.getClassLoader());
        List<String> all = new ArrayList<>();
        CATALOGS.forEach(c -> all.add("org.tinygears.uom.jsr.systems." + c));
        QuantityTypes.types().forEach(t -> all.add("org.tinygears.uom.jsr.quantity." + t.getSimpleName() + "Factory"));
        for (String name : all) {
            for (Field f : Class.forName(name).getFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.get(null) == null) {
                    throw new AssertionError(name + "." + f.getName() + " is null");
                }
            }
        }
        if (SI.METRE != LengthFactory.SYSTEM_UNIT || SI.METRE != BaseUnits.METRE) {
            throw new AssertionError("SI.METRE is not the factory's unit");
        }
        System.out.println("ok");
    }
}
