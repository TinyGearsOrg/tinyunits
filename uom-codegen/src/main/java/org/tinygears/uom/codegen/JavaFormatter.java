/*
 * Copyright (c) 2026 TinyGears.org
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tinygears.uom.codegen;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Formats generated sources as the Spotless Eclipse step of the build does (pom.xml): the JDT formatter of the
 * dependencies of this module with the settings of an Eclipse profile file, so a generated file is also formatted.
 */
final class JavaFormatter {
    private final CodeFormatter formatter;

    /** A formatter with the settings of the Eclipse profile file {@code profile}. */
    JavaFormatter(Path profile) {
        Map<String, String> options = new HashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            NodeList settings = factory.newDocumentBuilder().parse(profile.toFile()).getElementsByTagName("setting");
            for (int i = 0; i < settings.getLength(); i++) {
                Element setting = (Element) settings.item(i);
                options.put(setting.getAttribute("id"), setting.getAttribute("value"));
            }
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw new GenerationException(profile.toString(), 0, "cannot read the formatter profile", e);
        }
        formatter = ToolFactory.createCodeFormatter(options, ToolFactory.M_FORMAT_EXISTING);
    }

    /** {@code source}, the content of the file {@code name}, formatted. */
    String format(String name, String source) {
        TextEdit edit = formatter.format(
                CodeFormatter.K_COMPILATION_UNIT | CodeFormatter.F_INCLUDE_COMMENTS,
                source,
                0,
                source.length(),
                0,
                "\n");
        if (edit == null) {
            throw new GenerationException(name, 0, "the formatter cannot parse the generated source");
        }
        Document document = new Document(source);
        try {
            edit.apply(document);
        } catch (BadLocationException e) {
            throw new GenerationException(name, 0, "the formatter failed", e);
        }
        return document.get();
    }
}
