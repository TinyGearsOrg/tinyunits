// SPDX-License-Identifier: Apache-2.0

module org.tinygears.uom {
    requires transitive org.tinygears.uom.core;

    exports org.tinygears.uom.quantity;
    exports org.tinygears.uom.systems;

    provides javax.measure.spi.ServiceProvider with org.tinygears.uom.systems.UomServiceProvider;
}
