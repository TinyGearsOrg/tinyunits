// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.examples;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.format.QuantityFormat;
import javax.measure.format.UnitFormat;
import javax.measure.quantity.Speed;
import javax.measure.spi.FormatService;
import javax.measure.spi.ServiceProvider;
import org.tinygears.uom.quantity.Energy;
import org.tinygears.uom.quantity.Length;
import org.tinygears.uom.quantity.Mass;
import org.tinygears.uom.quantity.Temperature;
import org.tinygears.uom.quantity.Time;
import org.tinygears.uom.systems.SI;

/** Usage examples; each method returns what it prints so that a test can check it. */
public final class Examples {
    private Examples() {
    }

    /** Typed quantities: create, convert, add. */
    static String basics() {
        Length marathon = Length.of(42.195, SI.KILOMETRE);
        Length lap = Length.of(400, SI.METRE);
        // Strict overload: only Length can be added to a Length.
        Length total = marathon.add(lap);
        Length inMetres = total.to(SI.METRE);
        return total + " = " + inMetres;
    }

    /** Dividing quantities gives a quantity in system units; asType gives it its type back. */
    static String speed() {
        Quantity<?> ratio = Length.of(36, SI.KILOMETRE).divide(Time.of(1, SI.HOUR));
        Quantity<Speed> speed = ratio.asType(Speed.class);
        return speed.getValue() + " " + speed.getUnit();
    }

    /** Offset units: Celsius to kelvin and back. */
    static String temperature() {
        Temperature boiling = Temperature.of(100, SI.DEGREE_CELSIUS);
        return boiling.to(SI.KELVIN) + " | " + boiling.to(SI.KELVIN).to(SI.DEGREE_CELSIUS);
    }

    /** Equals is strict; isEquivalentTo compares what the quantities measure. */
    static String equality() {
        Length km = Length.of(2, SI.KILOMETRE);
        Length m = Length.of(2000, SI.METRE);
        return "equals=" + km.equals(m) + " equivalent=" + km.isEquivalentTo(m);
    }

    /** Units and quantities from text, and back to text. */
    static String formats() {
        FormatService formats = ServiceProvider.current().getFormatService();
        UnitFormat unitFormat = formats.getUnitFormat();
        QuantityFormat quantityFormat = formats.getQuantityFormat();
        Unit<?> millikelvin = unitFormat.parse("mK");
        Unit<?> squareKm = unitFormat.parse("km^2");
        Quantity<?> parsed = quantityFormat.parse("20 km");
        return unitFormat.format(SI.KILOMETRE) + " " + millikelvin + " " + squareKm
                + " | " + parsed.getValue() + " " + parsed.getUnit();
    }

    /** Plain JSR-385 code finds the implementation through the service provider. */
    static String provider() {
        ServiceProvider provider = ServiceProvider.current();
        Mass mass = Mass.of(2, SI.KILOGRAM);
        Quantity<javax.measure.quantity.Mass> viaFactory = provider
                .getQuantityFactory(javax.measure.quantity.Mass.class)
                .create(2, SI.KILOGRAM);
        return provider.getClass().getSimpleName() + " " + mass.isEquivalentTo(viaFactory);
    }

    /** Derived units keep their dimension: the product of force and length is energy. */
    static String work() {
        Quantity<?> product = org.tinygears.uom.quantity.Force.of(10, SI.NEWTON).multiply(Length.of(3, SI.METRE));
        Energy work = (Energy) product.asType(javax.measure.quantity.Energy.class);
        return work.to(SI.JOULE).getValue() + " J";
    }

    static Map<String, String> all() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("basics", basics());
        out.put("speed", speed());
        out.put("temperature", temperature());
        out.put("equality", equality());
        out.put("formats", formats());
        out.put("provider", provider());
        out.put("work", work());
        return out;
    }

    public static void main(String[] args) {
        all().forEach((name, result) -> System.out.println(name + ": " + result));
    }
}
