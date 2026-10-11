package org.tinygears.tinyunits.processor;

import org.tinygears.tinyunits.QuantityType;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/** Generates {@code XxxImpl} and {@code XxxFactory} for every {@link QuantityType} interface. */
@SupportedAnnotationTypes("org.tinygears.tinyunits.QuantityType")
public final class QuantityProcessor extends AbstractProcessor {

    private static final String QUANTITY = "org.tinygears.tinyunits.Quantity";

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        for (Element element : env.getElementsAnnotatedWith(QuantityType.class)) {
            if (!isValid(element)) {
                continue;
            }
            TypeElement type = (TypeElement) element;
            try {
                write(type, "Impl", IMPL, type.getAnnotation(QuantityType.class));
                write(type, "Factory", FACTORY, type.getAnnotation(QuantityType.class));
            } catch (IOException e) {
                error(element, "could not generate sources: " + e.getMessage());
            }
        }
        return true;
    }

    private boolean isValid(Element element) {
        TypeElement type = (TypeElement) element;
        if (element.getKind() != ElementKind.INTERFACE
                || type.getNestingKind() != NestingKind.TOP_LEVEL
                || !element.getModifiers().contains(Modifier.PUBLIC)) {
            error(element, "@QuantityType requires a public top-level interface");
            return false;
        }
        boolean selfTyped = type.getInterfaces().stream().anyMatch(i -> isQuantityOf(i, type));
        if (!selfTyped) {
            error(element, "interface must extend Quantity<" + type.getSimpleName() + ">");
        }
        return selfTyped;
    }

    private boolean isQuantityOf(TypeMirror mirror, TypeElement type) {
        if (!(mirror instanceof DeclaredType declared)) {
            return false;
        }
        TypeElement element = (TypeElement) declared.asElement();
        return element.getQualifiedName().contentEquals(QUANTITY)
                && declared.getTypeArguments().size() == 1
                && processingEnv.getTypeUtils().isSameType(declared.getTypeArguments().get(0), type.asType());
    }

    private void write(TypeElement type, String suffix, String template, QuantityType q) throws IOException {
        String pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        String name = type.getSimpleName().toString();
        String source = template
                .replace("$PKG$", pkg.isEmpty() ? "" : "package " + pkg + ";")
                .replace("$Q$", name)
                .replace("$SYMBOL$", q.systemUnit())
                .replace("$DIM$", String.join(", ",
                        String.valueOf(q.length()), String.valueOf(q.mass()), String.valueOf(q.time()),
                        String.valueOf(q.current()), String.valueOf(q.temperature()),
                        String.valueOf(q.amount()), String.valueOf(q.luminosity())));

        JavaFileObject file = processingEnv.getFiler()
                .createSourceFile((pkg.isEmpty() ? "" : pkg + ".") + name + suffix, type);
        try (Writer w = file.openWriter()) {
            w.write(source);
        }
    }

    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private static final String IMPL = """
            $PKG$

            import org.tinygears.tinyunits.Unit;

            import java.util.Objects;

            final class $Q$Impl implements $Q$ {
                private final double value;
                private final Unit<$Q$> unit;

                $Q$Impl(double value, Unit<$Q$> unit) {
                    this.value = value;
                    this.unit = Objects.requireNonNull(unit);
                }

                @Override public double value() { return value; }

                @Override public Unit<$Q$> unit() { return unit; }

                @Override public $Q$ to(Unit<$Q$> target) {
                    return target.equals(unit) ? this : new $Q$Impl(unit.convert(value, target), target);
                }

                @Override public $Q$ add($Q$ addend) {
                    return new $Q$Impl(value + addend.value(unit), unit);
                }

                @Override public $Q$ subtract($Q$ subtrahend) {
                    return new $Q$Impl(value - subtrahend.value(unit), unit);
                }

                @Override public $Q$ negate() { return new $Q$Impl(-value, unit); }

                @Override public int compareTo($Q$ other) {
                    return Double.compare(unit.toSystem(value), other.unit().toSystem(other.value()));
                }

                @Override public boolean equals(Object o) {
                    return o instanceof $Q$Impl q && Double.compare(value, q.value) == 0 && unit.equals(q.unit);
                }

                @Override public int hashCode() { return Objects.hash(value, unit); }

                @Override public String toString() { return value + " " + unit.symbol(); }
            }
            """;

    private static final String FACTORY = """
            $PKG$

            import org.tinygears.tinyunits.Dimension;
            import org.tinygears.tinyunits.IncommensurableException;
            import org.tinygears.tinyunits.Unit;

            public final class $Q$Factory {
                public static final Dimension DIMENSION = new Dimension($DIM$);
                public static final Unit<$Q$> SYSTEM_UNIT = unit("$SYMBOL$", 1);

                private $Q$Factory() {}

                public static $Q$ of(double value, Unit<$Q$> unit) {
                    return new $Q$Impl(value, unit);
                }

                public static Unit<$Q$> unit(String symbol, double factor) {
                    return unit(symbol, factor, 0);
                }

                public static Unit<$Q$> unit(String symbol, double factor, double offset) {
                    return new Unit<>(symbol, DIMENSION, factor, offset);
                }

                /** Types a derived unit, e.g. {@code metre.divide(second)}; fails if the dimension differs. */
                public static Unit<$Q$> from(Unit<?> unit) {
                    if (!DIMENSION.equals(unit.dimension())) {
                        throw new IncommensurableException(unit + " is not a unit of $Q$");
                    }
                    return unit(unit.symbol(), unit.factor(), unit.offset());
                }
            }
            """;
}
