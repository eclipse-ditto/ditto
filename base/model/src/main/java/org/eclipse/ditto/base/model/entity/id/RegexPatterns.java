/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.entity.id;

import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

@Immutable
public final class RegexPatterns {

    private static final String SLASH = "/";

    /**
     * The delimiter between namespace and name in an entity ID.
     */
    public static final String NAMESPACE_DELIMITER = ":";

    /**
     * Contains control characters.
     * <p>
     * Should the restrictions be too strict and üöäÜÖÄ and ß should be valid again:
     * [^\x00-\x1F\x7F-\xC3\xC5-\xD5\xD7-\xDB\xDD-\xDE\xE0-\xE3\xE5-\xF5\xF7-\xFB\xFD-\xFF]
     *
     * @since 1.2.0
     */
    public static final String CONTROL_CHARS = "\\x00-\\x1F\\x7F-\\xFF";

    private static final String NO_CONTROL_CHARS = "[^" + CONTROL_CHARS + "]";

    private static final String NO_CONTROL_CHARS_NO_SLASHES = "[^" + CONTROL_CHARS + SLASH + "]";

    /**
     * Name of the namespace group in the entity ID regex.
     */
    public static final String NAMESPACE_GROUP_NAME = "ns";

    /**
     * Name of the entity name group in the entity ID regex.
     */
    public static final String ENTITY_NAME_GROUP_NAME = "name";

    /**
     * Defines which characters are allowed to use in a namespace.
     */
    public static final String ALLOWED_NAMESPACE_CHARACTERS_REGEX = "[a-zA-Z]\\w*+";

    /**
     * Defines which characters are allowed to use in a name of an entity.
     */
    public static final String ALLOWED_CHARACTERS_IN_NAME = NO_CONTROL_CHARS_NO_SLASHES;

    /**
     * Adds additional characters that are allowed inside a namespace segment. It's defined as separate constant because
     * namespaces are not allowed to start with those characters.
     *
     * @since 2.2.0
     */
    public static final String ALLOWED_NAMESPACE_CHARACTERS_REGEX_INNER =
            "[.-]" + ALLOWED_NAMESPACE_CHARACTERS_REGEX;

    /**
     * The regex pattern for namespaces which validates that the namespace conforms the java package notation.
     */
    public static final String NAMESPACE_REGEX = "(?<" + NAMESPACE_GROUP_NAME + ">|(?:" +
            "(?:" + ALLOWED_NAMESPACE_CHARACTERS_REGEX + ")" +
            "(?:" + ALLOWED_NAMESPACE_CHARACTERS_REGEX_INNER + ")*+))";

    /**
     * The regex pattern for an Entity Name.
     */
    public static final String ENTITY_NAME_REGEX =
            "(?<" + ENTITY_NAME_GROUP_NAME + ">" + ALLOWED_CHARACTERS_IN_NAME + "++)";

    /**
     * The regex pattern for an Entity ID.
     * Combines "namespace" pattern (java package notation + a colon) and "name" pattern.
     */
    public static final String ID_REGEX = NAMESPACE_REGEX + NAMESPACE_DELIMITER + ENTITY_NAME_REGEX;

    /**
     * The compiled regex pattern for namespaces.
     */
    public static final Pattern NAMESPACE_PATTERN = Pattern.compile(NAMESPACE_REGEX);

    /**
     * The compiled regex pattern for entity names.
     */
    public static final Pattern ENTITY_NAME_PATTERN = Pattern.compile(ENTITY_NAME_REGEX);

    /**
     * The compiled regex pattern for namespaced entity IDs.
     */
    public static final Pattern ID_PATTERN = Pattern.compile(ID_REGEX);

    /**
     * Pattern for string which may not contain control characters.
     *
     * @since 1.2.0
     */
    public static final Pattern NO_CONTROL_CHARS_PATTERN = Pattern.compile("^" + NO_CONTROL_CHARS + "+$");

    /**
     * Pattern for string which may not contain control characters and no slashes.
     *
     * @since 1.2.0
     */
    public static final Pattern NO_CONTROL_CHARS_NO_SLASHES_PATTERN =
            Pattern.compile("^" + NO_CONTROL_CHARS_NO_SLASHES + "+$");

    private RegexPatterns() {
        throw new AssertionError();
    }
}
