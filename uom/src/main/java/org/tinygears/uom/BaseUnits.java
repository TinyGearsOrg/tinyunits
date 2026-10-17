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

import org.tinygears.uom.engine.UnitAtom;

/**
 * The engine units the generated system units and catalogs are built from: the seven SI base units, with the labels
 * of the JSR-385 side, and one.
 */
public final class BaseUnits {
    /** The metre, {@code m}. */
    public static final org.tinygears.uom.engine.Unit METRE = org.tinygears.uom.engine.Unit
            .base(UnitAtom.METRE, "metre");
    /** The kilogram, {@code kg}. */
    public static final org.tinygears.uom.engine.Unit KILOGRAM = org.tinygears.uom.engine.Unit
            .base(UnitAtom.KILOGRAM, "kilogram");
    /** The second, {@code s}. */
    public static final org.tinygears.uom.engine.Unit SECOND = org.tinygears.uom.engine.Unit
            .base(UnitAtom.SECOND, "second");
    /** The ampere, {@code A}. */
    public static final org.tinygears.uom.engine.Unit AMPERE = org.tinygears.uom.engine.Unit
            .base(UnitAtom.AMPERE, "ampere");
    /** The kelvin, {@code K}. */
    public static final org.tinygears.uom.engine.Unit KELVIN = org.tinygears.uom.engine.Unit
            .base(UnitAtom.KELVIN, "kelvin");
    /** The mole, {@code mol}. */
    public static final org.tinygears.uom.engine.Unit MOLE = org.tinygears.uom.engine.Unit.base(UnitAtom.MOLE, "mole");
    /** The candela, {@code cd}. */
    public static final org.tinygears.uom.engine.Unit CANDELA = org.tinygears.uom.engine.Unit
            .base(UnitAtom.CANDELA, "candela");
    /** One, {@code 1}. */
    public static final org.tinygears.uom.engine.Unit ONE = org.tinygears.uom.engine.Unit.ONE;

    private BaseUnits() {
    }
}
