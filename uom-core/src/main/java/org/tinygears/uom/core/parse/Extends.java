// SPDX-License-Identifier: Apache-2.0

package org.tinygears.uom.core.parse;

/**
 * An {@code @extends file} directive of the root file.
 *
 * @param file the extended file
 * @param source the file the line is in
 * @param line the 1-based line number
 */
public record Extends(String file, String source, int line) {
}
