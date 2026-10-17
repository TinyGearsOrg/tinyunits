/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.tck;

import java.util.Collection;
import java.util.List;
import javax.measure.Dimension;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.UnitConverter;
import javax.measure.format.QuantityFormat;
import javax.measure.format.UnitFormat;
import javax.measure.spi.QuantityFactory;

import tech.units.tck.util.ServiceConfiguration;

import org.tinygears.uom.engine.Factor;
import org.tinygears.uom.jsr.BaseUnits;
import org.tinygears.uom.jsr.Converters;
import org.tinygears.uom.jsr.UomDimension;
import org.tinygears.uom.jsr.UomUnit;
import org.tinygears.uom.jsr.quantity.QuantityTypes;
import org.tinygears.uom.jsr.systems.SI;
import org.tinygears.uom.jsr.systems.UomQuantityFormat;
import org.tinygears.uom.jsr.systems.UomUnitFormat;

/** Tells the JSR-385 TCK which uom classes, units, converters and formats to test. */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class UomTckConfiguration implements ServiceConfiguration {

    @Override
    public Collection<Class> getQuantityClasses() {
        return QuantityTypes.types().stream().map(type -> quantityClass((Class) type)).toList();
    }

    private static <Q extends Quantity<Q>> Class<?> quantityClass(Class<Q> type) {
        QuantityFactory<Q> factory = QuantityTypes.factory(type);
        return factory.create(1, factory.getSystemUnit()).getClass();
    }

    @Override
    public Collection<Class> getUnitClasses() {
        return List.of(UomUnit.class);
    }

    @Override
    public Collection<Class> getDimensionClasses() {
        return List.of(UomDimension.class);
    }

    @Override
    public Collection<Class<? extends Quantity>> getSupportedQuantityTypes() {
        return (Collection) QuantityTypes.types();
    }

    @Override
    public <Q extends Quantity<Q>> Unit<Q> getUnit4Type(Class<Q> quantityType) {
        return SI.SYSTEM.getUnit(quantityType);
    }

    @Override
    public Collection<? extends Unit<?>> getUnits4Test() {
        return List.of(
                SI.METRE,
                SI.KILOGRAM,
                SI.SECOND,
                SI.AMPERE,
                SI.KELVIN,
                SI.MOLE,
                SI.CANDELA,
                BaseUnits.ONE,
                SI.KILOMETRE,
                SI.DEGREE_CELSIUS,
                SI.NEWTON,
                SI.METRE.divide(SI.SECOND));
    }

    @Override
    public Collection<Dimension> getBaseDimensions() {
        return List.of(
                UomDimension.LENGTH,
                UomDimension.MASS,
                UomDimension.TIME,
                UomDimension.CURRENT,
                UomDimension.TEMPERATURE,
                UomDimension.AMOUNT,
                UomDimension.LUMINOSITY);
    }

    @Override
    public Collection<UnitConverter> getUnitConverters4Test() {
        return List
                .of(Converters.IDENTITY, Converters.scale(Factor.ofLong(1000)), Converters.shift(Factor.ofLong(273)));
    }

    @Override
    public Collection<UnitFormat> getUnitFormats4Test() {
        return List.of(UomUnitFormat.standard());
    }

    @Override
    public Collection<QuantityFormat> getQuantityFormats4Test() {
        return List.of(new UomQuantityFormat(UomUnitFormat.standard()));
    }
}
