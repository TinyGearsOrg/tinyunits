package org.tinygears.tinyunits.processor;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantityProcessorTest {

    private static List<Diagnostic<? extends JavaFileObject>> run(String name, String source) {
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///" + name + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignore) { return source; }
        };
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = compiler.getTask(null, null, diagnostics,
                List.of("-proc:only", "-classpath", System.getProperty("java.class.path")), null, List.of(file));
        task.setProcessors(List.of(new QuantityProcessor()));
        task.call();
        return diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).toList();
    }

    @Test
    void acceptsValidInterface() {
        var errors = run("Foo", """
                import org.tinygears.tinyunits.*;
                @QuantityType(systemUnit = "f", length = 1)
                public interface Foo extends Quantity<Foo> {}
                """);
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    void rejectsClass() {
        var errors = run("Foo", """
                import org.tinygears.tinyunits.*;
                @QuantityType(systemUnit = "f")
                public class Foo {}
                """);
        assertTrue(errors.stream().anyMatch(d -> d.getMessage(null).contains("public top-level interface")), errors.toString());
    }

    @Test
    void rejectsWrongSelfType() {
        var errors = run("Foo", """
                import org.tinygears.tinyunits.*;
                @QuantityType(systemUnit = "f")
                public interface Foo extends Quantity<Bar> {}
                interface Bar extends Quantity<Bar> {}
                """);
        assertTrue(errors.stream().anyMatch(d -> d.getMessage(null).contains("must extend Quantity<Foo>")), errors.toString());
    }
}
