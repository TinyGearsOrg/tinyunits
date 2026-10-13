# Security

## Supported versions

uom has not been released yet. Until 1.0, only the latest release (and the `main` branch) receives fixes; from 1.0,
the latest minor release of the current major version does.

## Reporting a vulnerability

Do not open a public issue. Report it privately through GitHub's private vulnerability reporting of
[TinyGearsOrg/tinyunits](https://github.com/TinyGearsOrg/tinyunits/security/advisories/new), or by email to the
maintainer, Thomas Neidhart (thomas.neidhart@gmail.com). Include the version, a minimal reproduction and the impact you
see. You get an answer within two weeks; a fix is released before the details are published.

## Java serialization

Units and quantities are `Serializable` through serialization proxies. A proxy rebuilds the object through the public
factories, so reading re-checks the invariants and a forged stream that violates them fails with
`InvalidObjectException`. One known gap: the steps of a multi-step converter that do not fold exactly and have a part
over 4096 bits are not checked against its stored factor and offset. A quantity reads back through the factory of the
current `ServiceProvider`. Systems and formats are not serializable. The serialized form may change in any release
before 1.0 (see [docs/API.md](docs/API.md)).

The checks run after the JDK has read the stream: the JDK allocates its arrays and strings, and resolves its classes,
before any of our code runs. Never read a stream you do not trust without an `ObjectInputFilter` that limits the sizes
and allows only the needed classes. This filter, also used by `serialization()` in
[`Examples.java`](uom-examples/src/main/java/org/tinygears/uom/examples/Examples.java), is a starting point:

```
maxarray=65536;maxbytes=1048576;maxdepth=20;maxrefs=10000;org.tinygears.uom.core.**;org.tinygears.uom.quantity.*;javax.measure.**;java.lang.*;java.math.*;!*
```

The allow-list needs `org.tinygears.uom.quantity` too, because the filter also checks the classes the proxies resolve
to. Set it for the whole JVM with `-Djdk.serialFilter=<filter>`, or per stream:

```java
String filter = "maxarray=65536;maxbytes=1048576;maxdepth=20;maxrefs=10000;"
        + "org.tinygears.uom.core.**;org.tinygears.uom.quantity.*;javax.measure.**;java.lang.*;java.math.*;!*";
Length length = Length.of(new BigDecimal("1.50"), SI.KILOMETRE);
ByteArrayOutputStream bytes = new ByteArrayOutputStream();
try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
    out.writeObject(length);
}
try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
    in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(filter));
    Object read = in.readObject();
    System.out.println(read + " " + read.equals(length));  // prints: 1.50 km true
}
```

## User `.units` files

Loading a `.units` file runs no code, but its `~` lines register symbols for the life of the JVM, and its includes
read files: load files only from trusted locations. See the caveats in [docs/CUSTOM-UNITS.md](docs/CUSTOM-UNITS.md).
