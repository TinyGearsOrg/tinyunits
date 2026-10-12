// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.serial;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.measure.MeasurementException;
import javax.measure.UnitConverter;
import org.tinygears.uom.core.BaseUnits;
import org.tinygears.uom.core.Converters;
import org.tinygears.uom.core.Factor;
import org.tinygears.uom.core.UnitAtom;
import org.tinygears.uom.core.UomConverter;
import org.tinygears.uom.core.UomUnit;

/** Subprocess check: reads golden units in a fresh JVM, then round trips in that runtime. Arg: golden dir. */
public final class SerialCheck {

    private SerialCheck() {
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        UomUnit<?> hz = (UomUnit<?>) read(Files.readAllBytes(dir.resolve("hertz.ser")));
        UomUnit<?> degree = (UomUnit<?>) read(Files.readAllBytes(dir.resolve("degree.ser")));
        check("Hz".equals(hz.getSymbol()) && "hertz".equals(hz.getName()), "hertz labels: " + hz);
        check("°".equals(degree.getSymbol()) && "degree".equals(degree.getName()), "degree labels: " + degree);
        // Reading registered the alternate atoms: binding their symbols to other units now fails.
        for (String symbol : List.of("Hz", "rad")) {
            try {
                BaseUnits.METRE.alternate(symbol);
                throw new AssertionError(symbol + " was not registered by reading");
            } catch (MeasurementException expected) {
                // registered
            }
        }
        check(hz.equals(BaseUnits.ONE.divide(BaseUnits.SECOND).alternate("Hz")), "hertz differs from Hz");

        UomUnit<?> celsius = BaseUnits.KELVIN.shift(273.15);
        check(celsius.equals(read(write(celsius))), "offset unit round trip");
        UomConverter a = Converters.scale(Factor.ofLong(3));
        UomConverter b = Converters.shift(Factor.ofLong(5));
        List<? extends UnitConverter> steps = a.concatenate(b).getConversionSteps();
        check(steps.equals(((UomConverter) read(write(a.concatenate(b)))).getConversionSteps()), "step order");
        List<String> symbols = hz.atoms().keySet().stream().map(UnitAtom::symbol).toList();
        System.out.println("ok named=" + UomUnit.class.getModule().isNamed() + " atoms=" + symbols);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static byte[] write(Object o) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        return bytes.toByteArray();
    }

    private static Object read(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }
}
