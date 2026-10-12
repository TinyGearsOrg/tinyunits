// SPDX-License-Identifier: Apache-2.0

module org.tinygears.uom.core {
    requires transitive java.measure;
    requires java.compiler;

    exports org.tinygears.uom.core;
    exports org.tinygears.uom.core.parse;
    exports org.tinygears.uom.core.quantity;
}
