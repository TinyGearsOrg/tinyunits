/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.codegen;

import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.tinygears.uom.core.parse.DefinitionParser;
import org.tinygears.uom.core.parse.Definitions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantityTypesParserTest {
    private static final String BASE = """
            m:metre !L
            kg:kilogram !M
            s:second !T
            A:ampere !I
            K:kelvin !Θ
            mol:mole !N
            cd:candela !J
            Hz:hertz ~ 1/s [Frequency]
            J:joule kg m^2 / s^2 [Energy]
            """;

    private static Definitions defs(String text) {
        return DefinitionParser.parse("test.units", new StringReader(text), null);
    }

    private static List<QuantityTypeSpec> parse(String text) {
        return QuantityTypesParser.parse("types.txt", new StringReader(text), defs(BASE));
    }

    private static void fails(String text, String message) {
        GenerationException e = assertThrows(GenerationException.class, () -> parse(text));
        assertEquals(message, e.getMessage());
    }

    @Test
    void happyPath() {
        Definitions d = defs(BASE);
        List<QuantityTypeSpec> l = QuantityTypesParser.parse(
                "types.txt",
                new StringReader(
                        "Length m\nSpeed m / s\nFrequency Hz\nEnergy J\nDimensionless 1\n"),
                d);
        assertEquals(
                List.of("Length", "Speed", "Frequency", "Energy", "Dimensionless"),
                l.stream().map(QuantityTypeSpec::name).toList());
        assertEquals(
                d.definitions().stream().filter(x -> x.symbol().equals("Hz")).findFirst().get().unit(),
                l.get(2).systemUnit());
        assertEquals("J", l.get(3).systemSymbol());
        assertEquals("joule", l.get(3).systemName());
        assertNull(l.get(1).systemSymbol());
        assertNull(l.get(1).systemName());
        assertEquals("m", l.get(0).systemSymbol());
        assertEquals("metre", l.get(0).systemName());
        assertEquals("m / s", l.get(1).formula());
        assertEquals("types.txt", l.get(1).source());
        assertEquals(2, l.get(1).line());
        assertNull(l.get(4).systemSymbol());
    }

    @Test
    void explicitSymbolAndName() {
        List<QuantityTypeSpec> l = parse(
                "Speed m / s => m/s:metre_per_second # c\nDimensionless 1 => 1:one\n"
                        + "Length m => m:metre\nAcceleration m / s^2 =>m/s^2:metre_per_second_squared\n");
        assertEquals("m / s", l.get(0).formula());
        assertEquals("m/s", l.get(0).systemSymbol());
        assertEquals("metre per second", l.get(0).systemName());
        assertEquals("1", l.get(1).systemSymbol());
        assertEquals("one", l.get(1).systemName());
        assertEquals("m", l.get(2).systemSymbol());
        assertEquals("metre", l.get(2).systemName());
        assertEquals("m/s^2", l.get(3).systemSymbol());
        assertEquals("metre per second squared", l.get(3).systemName());
        assertTrue(l.get(0).explicit());
        assertTrue(l.get(1).explicit());
        assertFalse(l.get(2).explicit());
    }

    @Test
    void explicitSymbolAndNameErrors() {
        fails("Speed m / s => m/s", "types.txt:1: expected 'symbol:name' after '=>', got 'm/s'");
        fails("Speed m / s => m/s:", "types.txt:1: expected 'symbol:name' after '=>', got 'm/s:'");
        fails(
                "Speed m / s => :metre_per_second",
                "types.txt:1: expected 'symbol:name' after '=>', got ':metre_per_second'");
        fails("Speed m / s =>", "types.txt:1: expected 'symbol:name' after '=>', got ''");
        fails("Speed m / s => m/s:a:b", "types.txt:1: extra ':' in 'm/s:a:b' after '=>'");
        fails(
                "Speed m / s => m /s:metre_per_second",
                "types.txt:1: whitespace in 'm /s:metre_per_second' after '=>'");
        fails("Length m => m:meter", "types.txt:1: explicit name for an already named unit 'm'");
        fails("Length m => metre:metre", "types.txt:1: explicit name for an already named unit 'm'");
        fails("Energy kg m^2 / s^2 => J:joule", "types.txt:1: duplicate symbol 'J'");
        fails("Speed m / s => m/s:velocity\nAcceleration m / s => m/s:other", "types.txt:2: duplicate symbol 'm/s'");
        fails("Speed m / s => m/s:___", "types.txt:1: blank name in 'm/s:___' after '=>'");
        fails(
                "Speed m / s => m/s:metre\u00A0per_second",
                "types.txt:1: whitespace in 'm/s:metre\u00A0per_second' after '=>'");
        fails("Speed => m/s:metre_per_second", "types.txt:1: missing system unit");
    }

    @Test
    void explicitSymbolMustEvaluateToTheUnit() {
        for (String symbol : List.of("m/s²", "v", "2v", "pi", "m", "m/s^2")) {
            fails(
                    "Speed m / s => " + symbol + ":metre_per_second",
                    "types.txt:1: explicit symbol '" + symbol + "' does not evaluate to the unit");
        }
        fails("Dimensionless 1 => one:one", "types.txt:1: explicit symbol 'one' does not evaluate to the unit");
        // m/s² reads as an exponent, so it is a valid explicit symbol for the acceleration.
        assertEquals("m/s²", parse("Acceleration m / s^2 => m/s²:metre_per_second_squared").get(0).systemSymbol());
    }

    @Test
    void arrowInACommentIsNoExplicitName() {
        QuantityTypeSpec speed = parse("Speed m / s # => x:y\n").get(0);
        assertEquals("m / s", speed.formula());
        assertNull(speed.systemSymbol());
        assertNull(speed.systemName());
        assertFalse(speed.explicit());
    }

    @Test
    void invalidName() {
        fails("length m", "types.txt:1: invalid quantity type name 'length'");
    }

    @Test
    void duplicate() {
        fails("Length m\n\nLength m", "types.txt:3: duplicate quantity type 'Length'");
    }

    @Test
    void unknownType() {
        fails("Foo m", "types.txt:1: unknown quantity type 'Foo'");
        fails("String m", "types.txt:1: unknown quantity type 'String'");
    }

    @Test
    void missingSystemUnit() {
        fails("Length", "types.txt:1: missing system unit");
        fails("Length # c", "types.txt:1: missing system unit");
    }

    @Test
    void unknownUnit() {
        fails("Length zz", "types.txt:1: unknown unit 'zz'");
    }

    @Test
    void scaledUnit() {
        Definitions d = defs(BASE + "km:kilometre 1000 m\n");
        GenerationException e = assertThrows(
                GenerationException.class,
                () -> QuantityTypesParser.parse("types.txt", new StringReader("Length km"), d));
        assertEquals("types.txt:1: system unit must be an unscaled unit", e.getMessage());
        assertEquals(1, e.line());
        assertEquals("types.txt", e.source());
    }

    @Test
    void lexicalForms() {
        assertEquals(
                List.of("Length", "Energy"),
                parse("﻿# head\r\nLength\tm # c\r\n\r\nEnergy J\r\n").stream()
                        .map(QuantityTypeSpec::name).toList());
        assertEquals(2, parse("# x\rLength m\rEnergy J").size());
        assertEquals("m", parse("Length   m   # c").get(0).formula());
    }

    @Test
    void emptyFile() {
        assertEquals(List.of(), parse(""));
        assertEquals(List.of(), parse("\n# only\n"));
    }
}
