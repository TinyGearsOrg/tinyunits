// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.systems.modulepath;

import javax.measure.spi.ServiceProvider;

/** Run by {@code ModulePathTest} in a subprocess; a package of its own so it does not split the module's package. */
public final class PrintCurrentProvider {
    private PrintCurrentProvider() {
    }

    public static void main(String[] args) {
        Class<?> c = ServiceProvider.current().getClass();
        System.out.println(c.getName() + " in " + c.getModule().getName());
    }
}
