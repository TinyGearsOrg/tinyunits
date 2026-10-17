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

/** One line of the relations file: {@code result = left * right} or {@code result = left / right}. */
public record QuantityRelation(String result, String left, boolean divide, String right, String source, int line) {

    /** A typed product method a relation implies: {@code receiver.multiply(argument)} or {@code divide} is a {@code result}. */
    public record Method(String receiver, boolean divide, String argument, String result, QuantityRelation from) {}
}
