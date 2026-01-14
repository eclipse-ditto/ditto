/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.wot.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enum defining the standard prefixes that are implicitly available when using the standard
 * WoT (Web of Things) TD context (v1.1).
 * These prefixes do not need to be explicitly defined in the {@code @context} as they are
 * part of the W3C WoT TD specification.
 *
 * @see <a href="https://www.w3.org/2022/wot/td/v1.1">W3C WoT TD Context v1.1</a>
 * @since 3.9.0
 */
public enum WotStandardContextPrefixes {

    /**
     * The TD (Thing Description) vocabulary prefix.
     */
    TD("td"),

    /**
     * The JSON Schema vocabulary prefix.
     */
    JSONSCHEMA("jsonschema"),

    /**
     * The WoT Security vocabulary prefix.
     */
    WOTSEC("wotsec"),

    /**
     * The Hypermedia Controls vocabulary prefix.
     */
    HCTL("hctl"),

    /**
     * The HTTP vocabulary prefix.
     */
    HTV("htv"),

    /**
     * The Thing Model vocabulary prefix.
     */
    TM("tm"),

    /**
     * The Schema.org vocabulary prefix.
     */
    SCHEMA("schema"),

    /**
     * The RDF Schema vocabulary prefix.
     */
    RDFS("rdfs"),

    /**
     * The RDF vocabulary prefix.
     */
    RDF("rdf"),

    /**
     * The XML Schema vocabulary prefix.
     */
    XSD("xsd"),

    /**
     * The Dublin Core Terms vocabulary prefix.
     */
    DCT("dct");

    private static final Set<String> ALL_STANDARD_PREFIXES = Collections.unmodifiableSet(
            Arrays.stream(values())
            .map(WotStandardContextPrefixes::getPrefix)
            .collect(Collectors.toSet())
    );

    private final String prefix;

    WotStandardContextPrefixes(final String prefix) {
        this.prefix = prefix;
    }

    /**
     * Returns the prefix string.
     *
     * @return the prefix string.
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Checks whether the given prefix is a standard WoT context prefix.
     *
     * @param prefix the prefix to check.
     * @return {@code true} if the prefix is a standard WoT context prefix, {@code false} otherwise.
     */
    public static boolean isStandardPrefix(final String prefix) {
        return ALL_STANDARD_PREFIXES.contains(prefix);
    }

    /**
     * Returns a set of all standard WoT context prefixes.
     *
     * @return an unmodifiable set of all standard prefix strings.
     */
    public static Set<String> getAllStandardPrefixes() {
        return ALL_STANDARD_PREFIXES;
    }
}
