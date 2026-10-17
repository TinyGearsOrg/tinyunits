# Using uom next to other JSR-385 implementations

uom registers one JSR-385 service provider, `UomServiceProvider`, with the name `TinyGears` and priority 100. Another
implementation on the same class path or module path registers its own: Indriya 2.2 is named `Default` and has priority
10. unit-api picks the **current** provider, the one with the highest priority unless the program sets one with
`ServiceProvider.setCurrent`. Generic JSR-385 code that calls `ServiceProvider.current()` gets the factories, formats and
systems of units of that provider.

## Which provider is current

With uom and Indriya both present, uom is current because its priority is higher. To use Indriya for generic code, set it:
`ServiceProvider.setCurrent(ServiceProvider.of("Default"))`. Providers are found by name, `TinyGears` or `Default`; a
provider without a name is found only by the value of its `toString()`.

```java
import javax.measure.spi.ServiceProvider;
System.out.println(ServiceProvider.of("TinyGears").getPriority()); // prints: 100
System.out.println(ServiceProvider.current());                     // prints: TinyGears
```

## uom does not depend on the choice

Narrowing a quantity with `asType`, narrowing a unit with `asType` and reading a serialized quantity need the quantity
factory of uom, and they find it themselves: the current provider if it is uom's, otherwise uom's provider among
`ServiceProvider.available()`, which is kept after the first lookup. So they work whichever provider is current:

```java
Quantity<?> speed = Length.of(6, SI.METRE).divide(Time.of(2, SI.SECOND)).asType(javax.measure.quantity.Speed.class);
System.out.println(speed);                                     // prints: 3.0 m/s
```

The lookup uses the class loader of the first caller; in an application server with one copy of uom per application this
is the application's.

## How units print

An unnamed unit, such as `SI.HERTZ.prefix(MetricPrefix.MILLI)` or `SI.METRE.multiply(1000)`, prints as the standard format
of uom writes it: `mHz`, `km`. `uom-jsr` has no systems, so `UomUnit.toString()` asks the provider of uom for the text in
the same way as `asType` asks for factories. If there is no such provider, or the format has no text for the unit (a
negative factor, say), it prints the definition instead: `1|1000·Hz`, `-2·m`.

## What `uom-jsr` depends on

`uom-jsr` does not depend on **which** provider is current, and it has no compile-time dependency on the module `uom`.
Units, converters, the formula parser, offset handling and arithmetic never use a provider at all.

It does depend on **a** provider with quantity factories for `asType` and deserialization, because the table of quantity
types is in the module `uom`, not in `uom-jsr`:

| On the path | `asType` and deserialization | printing an unnamed unit |
|---|---|---|
| `uom` (with any other provider, current or not) | use the factories of uom | as the format of uom writes it |
| `uom-jsr` and a test or custom provider only | use that provider's factories (the `uom-jsr` tests do this) | the definition |
| `uom-jsr` and only another implementation's provider, such as Indriya | fail: its factory is of the other implementation, so `asType` throws `ClassCastException` | the definition |
| `uom-jsr` and no provider | fail with `IllegalStateException` | the definition |

Use the module `uom` unless you provide your own factories.

`ProviderCoexistenceTest` runs uom and Indriya together and checks both directions: uom with Indriya as the current
provider, and Indriya with uom as the current provider.

## What does not mix

Quantities and units of two implementations are not interchangeable. A uom quantity rejects a unit or quantity of
another implementation with `UnconvertibleException` (`add`, `to`, `isEquivalentTo` and the others), so convert by value
and unit explicitly. A quantity written by one implementation is not read by the other. Where the two behave differently
for the same code (equality, `isEquivalentTo`, formats), see [INDRIYA.md](INDRIYA.md).
