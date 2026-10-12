// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.parse;

import java.util.List;

/** The parsed content of a definition file and its includes, in file order. */
public record Definitions(List<Definition> definitions, List<Preferred> preferred, List<Prefixed> prefixed) {
    public Definitions {
        definitions = List.copyOf(definitions);
        preferred = List.copyOf(preferred);
        prefixed = List.copyOf(prefixed);
    }
}
