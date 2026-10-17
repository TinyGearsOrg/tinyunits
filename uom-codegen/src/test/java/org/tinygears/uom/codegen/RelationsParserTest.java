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

import org.tinygears.uom.engine.parse.DefinitionParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RelationsParserTest {
    private static final List<QuantityTypeSpec> TYPES = QuantityTypesParser.parse(
            "types.txt",
            new StringReader("Length m\nTime s\nSpeed m / s\nArea m^2\nFrequency 1 / s\nRadioactivity 1 / s\n"),
            DefinitionParser.parse("test.units", new StringReader("m:metre !L\ns:second !T\n"), null));

    private static List<QuantityRelation> parse(String text) {
        return RelationsParser.parse("relations.txt", new StringReader(text), TYPES);
    }

    private static List<String> methods(String text) {
        return RelationsParser.methods(parse(text)).stream().map(
                m -> m.result() + " " + m.receiver() + (m.divide() ? ".divide(" : ".multiply(") + m.argument() + ")")
                .toList();
    }

    private static void fails(String text, String message) {
        assertEquals(message, assertThrows(GenerationException.class, () -> parse(text)).getMessage());
    }

    @Test
    void eachRelationGivesFourMethods() {
        assertEquals(
                List.of(
                        "Speed Length.divide(Time)",
                        "Time Length.divide(Speed)",
                        "Length Time.multiply(Speed)",
                        "Length Speed.multiply(Time)"),
                methods("# comment\n\nSpeed = Length / Time  # trailing\n"));
    }

    @Test
    void aSelfProductGivesEachMethodOnce() {
        assertEquals(
                List.of("Area Length.multiply(Length)", "Length Area.divide(Length)"),
                methods("Area = Length*Length"));
    }

    @Test
    void relationsAreRecordedWithTheirLine() {
        assertEquals(
                List.of(new QuantityRelation("Speed", "Length", true, "Time", "relations.txt", 2)),
                parse("\nSpeed = Length / Time\n"));
    }

    @Test
    void errors() {
        fails("Speed = Length", "relations.txt:1: expected 'Result = Left * Right' or 'Result = Left / Right'");
        fails("Speed = Length / Mass", "relations.txt:1: unknown quantity type 'Mass'");
        fails("Area = Length / Time", "relations.txt:1: dimension of Length / Time is L·T^-1, not L^2 of Area");
        fails(
                "Frequency = Speed / Length\nRadioactivity = Speed / Length",
                "relations.txt:2: Speed.divide(Length) follows from line 1 too");
        fails(
                "Speed = Length / Time\nTime = Length / Speed",
                "relations.txt:2: Length.divide(Speed) follows from line 1 too");
    }
}
