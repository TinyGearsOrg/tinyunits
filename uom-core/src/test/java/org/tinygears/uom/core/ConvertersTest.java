// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tinygears.uom.core.Converters.IDENTITY;
import static org.tinygears.uom.core.Converters.affine;
import static org.tinygears.uom.core.Converters.scale;
import static org.tinygears.uom.core.Converters.shift;
import static org.tinygears.uom.core.Factor.ONE;
import static org.tinygears.uom.core.Factor.ZERO;
import static org.tinygears.uom.core.Factor.of;
import static org.tinygears.uom.core.Factor.ofLong;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import javax.measure.UnitConverter;
import org.junit.jupiter.api.Test;

class ConvertersTest {

    @Test
    void affineConvertsDouble() {
        UomConverter c = affine(ONE, Factor.decimal("273.15"));
        assertEquals(373.15, c.convert(100.0), 1e-12);
        assertEquals(100.0, c.inverse().convert(373.15), 1e-12);
    }

    @Test
    void affineConvertsDecimalExactly() {
        UomConverter c = affine(of(5, 9), of(-160, 9));
        assertEquals(0, c.convert(new BigDecimal("212"), MathContext.DECIMAL128).compareTo(new BigDecimal("100")));
    }

    @Test
    void numberKeepsType() {
        assertInstanceOf(Double.class, IDENTITY.convert(Double.valueOf(1)));
        assertInstanceOf(BigDecimal.class, IDENTITY.convert(new BigDecimal("1")));
    }

    @Test
    void concatenateOrder() {
        assertEquals(8.0, scale(ofLong(2)).concatenate(shift(ofLong(1))).convert(3.0));
    }

    @Test
    void concatenationEqualsFusedForm() {
        UomConverter fused = scale(ofLong(2)).concatenate(scale(ofLong(3)));
        assertEquals(scale(ofLong(6)), fused);
        assertEquals(2, fused.getConversionSteps().size());
        UomConverter six = scale(ofLong(6));
        assertEquals(1, six.getConversionSteps().size());
        assertTrue(six.getConversionSteps().contains(six));
    }

    @Test
    void flags() {
        assertTrue(IDENTITY.isIdentity());
        assertTrue(scale(ofLong(2)).isLinear());
        assertFalse(shift(ofLong(1)).isLinear());
        assertEquals(IDENTITY, affine(ONE, ZERO));
    }

    @Test
    void irrationalFactor() {
        assertEquals(1.4142135623730951, scale(ofLong(2).root(2)).convert(1.0));
    }

    @Test
    void zeroFactorCannotBeInverted() {
        assertThrows(ArithmeticException.class, () -> scale(ZERO).inverse());
    }

    @Test
    void foreignConverterRejected() {
        UnitConverter foreign = new UnitConverter() {
            public boolean isIdentity() { return false; }
            public boolean isLinear() { return true; }
            public UnitConverter inverse() { return this; }
            public Number convert(Number value) { return value; }
            public double convert(double value) { return value; }
            public UnitConverter concatenate(UnitConverter other) { return this; }
            public java.util.List<? extends UnitConverter> getConversionSteps() { return java.util.List.of(this); }
        };
        assertThrows(IllegalArgumentException.class, () -> IDENTITY.concatenate(foreign));
    }

    @Test
    void decimalRoundsOnceForLargeValues() {
        // Exact result is 1.00004999e32 (rounds down at 5 digits); a 5-digit 5/9 would push it up.
        BigDecimal v = new BigDecimal("1800089982").scaleByPowerOfTen(23).add(BigDecimal.valueOf(32));
        BigDecimal exact = v.subtract(BigDecimal.valueOf(32)).multiply(BigDecimal.valueOf(5))
                .divide(BigDecimal.valueOf(9), new MathContext(100));
        MathContext mc = new MathContext(5);
        assertEquals(0, affine(of(5, 9), of(-160, 9)).convert(v, mc).compareTo(exact.round(mc)));
    }

    @Test
    void exactDecimalConversionRoundsOnce() {
        // Exact result 0.1499...98 rounds to 0.1 at one digit; rounding to guard digits first gives 0.15, then 0.2.
        BigDecimal v = new BigDecimal("0.0749999999999999999999");
        assertEquals(new BigDecimal("0.1"), scale(ofLong(2)).convert(v, new MathContext(1)));
    }

    @Test
    void decimalUnlimitedPrecisionStaysExactForDecimalCoefficients() {
        UomConverter c = affine(Factor.decimal("1.5"), Factor.decimal("0.25"));
        assertEquals(0, c.convert(new BigDecimal("3"), MathContext.UNLIMITED).compareTo(new BigDecimal("4.75")));
    }

    @Test
    void irrationalConversionOfHugeValuesIsFast() {
        UomConverter degree = scale(Factor.PI.divide(ofLong(180)));
        BigDecimal perUnit = Factor.PI.divide(ofLong(180)).decimalValue(new MathContext(40));
        for (int e : new int[] {20000, 200000}) {
            BigDecimal v = BigDecimal.ONE.scaleByPowerOfTen(e);
            BigDecimal r = assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> degree.convert(v, MathContext.DECIMAL64));
            BigDecimal expected = perUnit.scaleByPowerOfTen(e);
            BigDecimal relative = r.subtract(expected).abs().divide(expected, MathContext.DECIMAL64);
            assertTrue(relative.compareTo(new BigDecimal("1e-15")) < 0, () -> e + ": " + r);
        }
    }
}
