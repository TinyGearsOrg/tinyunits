package org.tinygears.uom.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ModulePathTest {

    @Test
    void describesModuleOnModulePath() throws Exception {
        Path classes = Path.of("").toAbsolutePath().resolve("target/classes");
        Assumptions.assumeTrue(Files.isDirectory(classes), "no target/classes");
        String jar = null;
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (Path.of(entry).getFileName().toString().equals("unit-api-2.2.jar")) {
                jar = entry;
            }
        }
        Assumptions.assumeTrue(jar != null, "unit-api-2.2.jar not on the class path");

        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Process p = new ProcessBuilder(java, "--module-path", classes + File.pathSeparator + jar,
                "--describe-module", "org.tinygears.uom.core").redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(30, TimeUnit.SECONDS), "java did not finish in 30 seconds");
        assertEquals(0, p.exitValue(), out);
        assertTrue(out.contains("exports org.tinygears.uom.core.parse"), out);
        assertTrue(out.contains("exports org.tinygears.uom.core"), out);
        assertTrue(out.contains("requires java.measure transitive"), out);
    }
}
