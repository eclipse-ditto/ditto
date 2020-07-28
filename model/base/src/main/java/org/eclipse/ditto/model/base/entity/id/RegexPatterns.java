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
package org.eclipse.ditto.model.base.entity.id;

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
     *
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
     * Adds the dot to allowed characters. Its defined as separate constant because namespaces are not allowed to start
     * with dots.
     */
    public static final String ALLOWED_NAMESPACE_CHARACTERS_INCLUDING_DOT =
            "\\." + ALLOWED_NAMESPACE_CHARACTERS_REGEX;

    /**
     * The regex pattern for namespaces which validates that the namespace conforms the java package notation.
     */
    public static final String NAMESPACE_REGEX = "(?<" + NAMESPACE_GROUP_NAME + ">|(?:" +
            "(?:" + ALLOWED_NAMESPACE_CHARACTERS_REGEX + ")" +
            "(?:" + ALLOWED_NAMESPACE_CHARACTERS_INCLUDING_DOT + ")*+))";

    /**
     * Regex pattern that matches URL escapes. E.G. %3A for a colon (':').
     */
    public static final String URL_ESCAPES = "%\\p{XDigit}{2}";

    /**
     * Adds the $ to allowed characters. Its defined as separate constant because names are not allowed to start
     * with $.
     */
    public static final String ALLOWED_CHARACTERS_IN_NAME_INCLUDING_DOLLAR = ALLOWED_CHARACTERS_IN_NAME + "$";

    /**
     * First part of an entity name.
     */
    public static final String URI_PATH_SEGMENT = "(?:[" + ALLOWED_CHARACTERS_IN_NAME + "]|" + URL_ESCAPES + ")";

    /**
     * Second part of an entity name: This part allows the $ symbol.
     */
    public static final String URI_PATH_SEGMENT_INCLUDING_DOLLAR =
            "(?:[" + ALLOWED_CHARACTERS_IN_NAME_INCLUDING_DOLLAR + "]|" + URL_ESCAPES + ")";
    /**
     * The regex pattern for an Entity Name.
     */
    public static final String ENTITY_NAME_REGEX = "(?<" + ENTITY_NAME_GROUP_NAME +
            ">" + URI_PATH_SEGMENT + URI_PATH_SEGMENT_INCLUDING_DOLLAR + "*+)";

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
     * Pattern with error message for string which a namespaced entity ID has to conform to.
     *
     * @since 1.2.0
     */
    public static final PatternWithMessage ENTITY_ID_PATTERN = PatternWithMessage.of(
            ID_PATTERN,
            "The given identifier is not valid.");

    /**
     * Pattern with error message for string which may not contain control characters.
     *
     * @since 1.2.0
     */
    public static final PatternWithMessage NO_CONTROL_CHARS_PATTERN = PatternWithMessage.of(
            Pattern.compile("^" + NO_CONTROL_CHARS + "+$"),
            "No control characters are allowed.");

    /**
     * Pattern with error message for string which may not contain control characters and no slashes.
     *
     * @since 1.2.0
     */
    public static final PatternWithMessage NO_CONTROL_CHARS_NO_SLASHES_PATTERN = PatternWithMessage.of(
            Pattern.compile("^" + NO_CONTROL_CHARS_NO_SLASHES + "+$"),
            "Neither slashes nor any control characters are allowed as part of the JSON key.");


    private RegexPatterns() {
        throw new AssertionError();
    }

    /**
     * Wraps a compiled {@link Pattern} and an error message that can be used in an exception if the pattern did not
     * match.
     *
     * @since 1.2.0
     */
    public static class PatternWithMessage {

        private final Pattern pattern;
        private final String message;

        private PatternWithMessage(final Pattern pattern, final String message) {
            this.pattern = pattern;
            this.message = message;
        }

        public static PatternWithMessage of(final Pattern pattern, final String message) {
            return new PatternWithMessage(pattern, message);
        }

        public Pattern getPattern() {
            return pattern;
        }

        public String getMessage() {
            return message;
        }
    }
}
