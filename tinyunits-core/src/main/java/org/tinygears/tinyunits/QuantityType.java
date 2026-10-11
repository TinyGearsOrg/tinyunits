package org.tinygears.tinyunits;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public top-level interface {@code Foo extends Quantity<Foo>} for code generation of
 * {@code FooImpl} and {@code FooFactory}. The exponents give the dimension in SI base dimensions.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface QuantityType {

    /** Symbol of the system unit (the unit with factor 1 and offset 0), e.g. "m/s". */
    String systemUnit();

    int length() default 0;

    int mass() default 0;

    int time() default 0;

    int current() default 0;

    int temperature() default 0;

    int amount() default 0;

    int luminosity() default 0;
}
