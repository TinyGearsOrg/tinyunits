# Offset units, points and differences

Some units have an offset: `0 °C` is `273.15 K`, and `32 °F` is `0 °C`. The same number then means two different
things: a **point** on the scale ("it is 20 °C outside") or a **difference** between two points ("it is 5 °C warmer").
A point converts through the offset (20 °C is 293.15 K); a difference does not (a rise of 5 °C is a rise of 5 K).

uom keeps the two apart with the standard `Quantity.Scale` of every quantity:

| Scale | Meaning | How to get one |
|---|---|---|
| `ABSOLUTE` (default) | a point; conversions apply the offset | `Temperature.of(20, SI.DEGREE_CELSIUS)` |
| `RELATIVE` | a difference; conversions use the factor only | `Temperature.of(5, SI.DEGREE_CELSIUS, Quantity.Scale.RELATIVE)` |

For a unit without an offset (metre, kelvin, hertz, ...) the scale makes no difference to any result. It is kept and
compared by `equals`, written by serialization and not written by the format; a parse always gives `ABSOLUTE`.

## The rules

| Operation | Operands | Result |
|---|---|---|
| `to(unit)`, `toSystem(system)` | ABSOLUTE | converted through the offset, scale ABSOLUTE |
| | RELATIVE | converted by the factor only (5 K is 5 °C), scale RELATIVE |
| `add`, `subtract` | neither is RELATIVE | the literal rule below, scale ABSOLUTE |
| | one or both are RELATIVE | a plain delta: the right operand converted by the factor only and combined with the left value in the left unit; scale RELATIVE if both are RELATIVE, else ABSOLUTE |
| `multiply(Number)`, `divide(Number)` | ABSOLUTE | the literal rule below, scale ABSOLUTE |
| | RELATIVE | the value times or over the number, scale RELATIVE |
| `negate` | any | the value negated in the same unit, scale kept |
| `compareTo`, `isEquivalentTo` | both RELATIVE | compared as differences (factor only) |
| | otherwise | compared as system-unit values (offset applied) |

The **literal rule** is the one in the `javax.measure.Quantity` Javadoc: the operands behave as if they were first
converted to the system unit, combined, and the result converted back to the unit of the left operand. The Javadoc does
not mention `Scale`. For a unit without an offset the rule is plain arithmetic. For an offset unit it gives results
that are correct for conversion but odd for sums of points, because adding two points has no physical meaning.

Not covered by these rules: `multiply(Quantity)`, `divide(Quantity)` and `inverse()` take the system-unit values of
their operands, offsets included, whatever the scale, and return an `ABSOLUTE` quantity without a typed class
(`docs/TODO.md`, item 1).

## Examples

A point plus a difference is a point; a difference converts without the offset:

```java
Temperature boiling = Temperature.of(100, SI.DEGREE_CELSIUS);
Temperature rise = Temperature.of(5, SI.DEGREE_CELSIUS, Quantity.Scale.RELATIVE);
System.out.println(boiling.add(rise));            // prints: 105.0 °C
System.out.println(rise.to(SI.KELVIN));           // prints: 5.0 K
System.out.println(boiling.to(SI.KELVIN));        // prints: 373.15 K
```

A difference in kelvin is added to a point in degrees Celsius by its size, not through the offset:

```java
Temperature start = Temperature.of(2, SI.DEGREE_CELSIUS);
Temperature gain = Temperature.of(5, SI.KELVIN, Quantity.Scale.RELATIVE);
System.out.println(start.add(gain));              // prints: 7.0 °C
System.out.println(start.subtract(gain));         // prints: -3.0 °C
```

Two differences give a difference, and a difference can be scaled:

```java
Temperature a = Temperature.of(2, SI.DEGREE_CELSIUS, Quantity.Scale.RELATIVE);
Temperature b = Temperature.of(1, SI.DEGREE_CELSIUS, Quantity.Scale.RELATIVE);
System.out.println(a.add(b));                     // prints: 3.0 °C
System.out.println(a.multiply(2));                // prints: 4.0 °C
System.out.println(a.isEquivalentTo(Temperature.of(2, SI.KELVIN, Quantity.Scale.RELATIVE))); // prints: true
```

Two points follow the literal rule (the default scale is `ABSOLUTE`):

```java
Temperature two = Temperature.of(2, SI.DEGREE_CELSIUS);
Temperature one = Temperature.of(1, SI.DEGREE_CELSIUS);
System.out.println(two.add(one));                 // prints: 276.15 °C
System.out.println(two.subtract(one));            // prints: -272.15 °C
System.out.println(two.multiply(2));              // prints: 277.15 °C
```

To get the difference between two points, subtract them in a unit without an offset, and mark the result as a
difference when it should convert without the offset:

```java
Temperature later = Temperature.of(25, SI.DEGREE_CELSIUS);
Temperature earlier = Temperature.of(20, SI.DEGREE_CELSIUS);
Quantity<?> kelvin = later.to(SI.KELVIN).subtract(earlier.to(SI.KELVIN));
Temperature difference = Temperature.of(kelvin.getValue().doubleValue(), SI.KELVIN, Quantity.Scale.RELATIVE);
System.out.println(difference.to(SI.DEGREE_CELSIUS)); // prints: 5.0 °C
```

`BigDecimal` quantities follow the same rules and are exact, where `double` shows the rounding of the offset:

```java
Temperature hot = Temperature.of(new BigDecimal("20"), SI.DEGREE_CELSIUS);
Temperature zero = Temperature.of(new BigDecimal("0"), SI.DEGREE_CELSIUS);
System.out.println(hot.subtract(zero));           // prints: -253.15 °C
System.out.println(Temperature.of(20, SI.DEGREE_CELSIUS).subtract(Temperature.of(0, SI.DEGREE_CELSIUS)));
// prints: -253.14999999999998 °C
```

## Compatibility

- The behaviour for two `ABSOLUTE` operands is the Javadoc rule, and agrees with Indriya 2.2 (`2 °C + 1 °C` is
  `276.15 °C`; probed, not specified by the standard).
- For a `RELATIVE` operand uom and Indriya 2.2 agree on a point plus or minus a difference, a difference plus a
  difference, a difference times a number, and the conversion of a difference. They differ on a difference minus a point:
  Indriya 2.2 returned `-545.30 °C` for `2 °C` (relative) minus `1 °C` (absolute); uom returns the delta `1 °C`.
- The TCK (`unit-tck` 2.2) does not test `Scale` or offset arithmetic, so none of this is fixed by the standard.

## Open points

- A point minus a point is not returned as a difference; use the kelvin idiom above.
- Quantity-by-quantity products with a `RELATIVE` operand apply the offset (see above).
- `UomUnit.multiply(Unit)` and `divide(Unit)` of an offset unit throw (`docs/TODO.md`, item 3).
