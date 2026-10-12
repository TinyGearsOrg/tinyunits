// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ModulePathTest {

    @Test
    void describesModuleOnModulePath() throws Exception {
        Path classes = Path.of("").toAbsolutePath().resolve("target/classes");
        Path core = Path.of("").toAbsolutePath().resolve("../uom-core/target/classes").normalize();
        Path quantities = Path.of("").toAbsolutePath().resolve("../uom-quantities/target/classes").normalize();
        Assumptions.assumeTrue(Files.isDirectory(classes) && Files.isDirectory(core)
                && Files.isDirectory(quantities), "sibling classes not built");
        String jar = null;
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (Path.of(entry).getFileName().toString().equals("unit-api-2.2.jar")) {
                jar = entry;
            }
        }
        Assumptions.assumeTrue(jar != null, "unit-api-2.2.jar not on the class path");

        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String path = String.join(File.pathSeparator, classes.toString(), jar, core.toString(), quantities.toString());
        String out = run(List.of(java, "--module-path", path, "--describe-module", "org.tinygears.uom.systems"));
        assertTrue(out.contains("provides javax.measure.spi.ServiceProvider with org.tinygears.uom.systems.UomServiceProvider"), out);
        assertTrue(out.contains("requires org.tinygears.uom.quantities transitive"), out);
    }

    // Output goes to a file so waitFor can time out before anything blocks on the pipe.
    private static String run(List<String> command) throws Exception {
        Path log = Files.createTempFile("modulepath", ".log");
        Process p = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(p.waitFor(30, TimeUnit.SECONDS), "process did not finish within " + Duration.ofSeconds(30));
            String out = Files.readString(log);
            assertEquals(0, p.exitValue(), out);
            return out;
        } finally {
            if (p.isAlive()) {
                p.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(log);
        }
    }
}
