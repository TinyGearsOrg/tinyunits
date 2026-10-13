// SPDX-License-Identifier: Apache-2.0

/**
 * uom, a JSR-385 implementation: typed quantities ({@code org.tinygears.uom.quantity}), the unit catalogs of SI,
 * US customary, Imperial and the CGS systems, the unit and quantity formats and the service provider
 * ({@code org.tinygears.uom.systems}).
 */
module org.tinygears.uom {
    requires transitive org.tinygears.uom.core;

    exports org.tinygears.uom.quantity;
    exports org.tinygears.uom.systems;

    provides javax.measure.spi.ServiceProvider with org.tinygears.uom.systems.UomServiceProvider;
}
