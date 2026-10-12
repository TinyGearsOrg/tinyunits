package org.tinygears.uom.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModuleSmokeTest {

    @Test
    void unitApiIsVersion22() {
        String location = javax.measure.Unit.class.getProtectionDomain().getCodeSource().getLocation().toString();
        assertTrue(location.contains("unit-api-2.2"), location);
    }
}
