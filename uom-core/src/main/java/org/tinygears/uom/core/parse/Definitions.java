// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.parse;

import java.util.List;

/**
 * The parsed content of a definition file and its includes, in file order. {@code system}, {@code extended} and
 * {@code members} come from the root file only; {@code system} is {@code null} without an {@code @system} line.
 */
public record Definitions(List<Definition> definitions, List<Preferred> preferred, List<Prefixed> prefixed,
        String system, List<Extends> extended, List<Member> members) {
    public Definitions {
        definitions = List.copyOf(definitions);
        preferred = List.copyOf(preferred);
        prefixed = List.copyOf(prefixed);
        extended = List.copyOf(extended);
        members = List.copyOf(members);
    }
}
