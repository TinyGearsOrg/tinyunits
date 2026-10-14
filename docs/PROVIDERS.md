# Using uom next to other JSR-385 implementations

uom registers one JSR-385 service provider, `UomServiceProvider`, with the name `uom` and priority 100. Another
implementation on the same class path or module path registers its own: Indriya 2.2 is named `Default` and has priority
10. unit-api picks the **current** provider, the one with the highest priority unless the program sets one with
`ServiceProvider.setCurrent`. Generic JSR-385 code that calls `ServiceProvider.current()` gets the factories, formats and
systems of units of that provider.

## Which provider is current

With uom and Indriya both present, uom is current because its priority is higher. To use Indriya for generic code, set it:
`ServiceProvider.setCurrent(ServiceProvider.of("Default"))`. Providers are found by name, `uom` or `Default`; a
provider without a name is found only by the value of its `toString()`.

```java
import javax.measure.spi.ServiceProvider;
System.out.println(ServiceProvider.of("uom").getPriority());   // prints: 100
System.out.println(ServiceProvider.current());                 // prints: uom
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

## What `uom-core` depends on

`uom-core` does not depend on **which** provider is current, and it has no compile-time dependency on the module `uom`.
Units, converters, the formula parser, offset handling and arithmetic never use a provider at all.

It does depend on **a** provider with quantity factories for `asType` and deserialization, because the table of quantity
types is in the module `uom`, not in `uom-core`:

| On the path | `asType` and deserialization |
|---|---|
| `uom` (with any other provider, current or not) | use the factories of uom |
| `uom-core` and a test or custom provider only | use that provider's factories (the `uom-core` tests do this) |
| `uom-core` and only another implementation's provider, such as Indriya | fail: its factory is of the other implementation, so `asType` throws `ClassCastException` |
| `uom-core` and no provider | fail with `IllegalStateException` |

Use the module `uom` unless you provide your own factories.

`ProviderCoexistenceTest` runs uom and Indriya together and checks both directions: uom with Indriya as the current
provider, and Indriya with uom as the current provider.

## What does not mix

Quantities and units of two implementations are not interchangeable. A uom quantity rejects a unit or quantity of
another implementation with `UnconvertibleException` (`add`, `to`, `isEquivalentTo` and the others), so convert by value
and unit explicitly. A quantity written by one implementation is not read by the other.
