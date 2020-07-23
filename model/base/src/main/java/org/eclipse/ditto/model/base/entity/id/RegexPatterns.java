/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

    public final static String SLASH = "/";

    public static final String NAMESPACE_DELIMITER = ":";

    public final static String CONTROL_CHARS = "\\x00-\\x1F\\x7F-\\xFF";

    public final static String NO_CONTROL_CHARS = "[^" + CONTROL_CHARS + "]";

    public final static String NO_CONTROL_CHARS_NO_SLASHES = "[^" + CONTROL_CHARS + SLASH + "]";

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
     * Pattern that allows anything but {@link #CONTROL_CHARS} and {@link #SLASH}es.
     */
    private static final String NO_CONTROL_CHARS_NO_SLASHES_MESSAGE =
            "Neither slashes nor any control characters are allowed.";
    private final static PatternWithMessage NO_CONTROL_CHARS_NO_SLASHES_PATTERN =
            PatternWithMessage.of(Pattern.compile("^" + NO_CONTROL_CHARS_NO_SLASHES + "+$"),
                    NO_CONTROL_CHARS_NO_SLASHES_MESSAGE);

    /**
     * Pattern for feature identifiers.
     */
    public final static PatternWithMessage FEATURE_PATTERN = NO_CONTROL_CHARS_NO_SLASHES_PATTERN;

    /**
     * Pattern for attribute identifiers.
     */
    public final static PatternWithMessage ATTRIBUTE_PATTERN = NO_CONTROL_CHARS_NO_SLASHES_PATTERN;

    /**
     * Pattern for policy label identifiers.
     */
    public final static PatternWithMessage LABEL_PATTERN = NO_CONTROL_CHARS_NO_SLASHES_PATTERN;

    /**
     * The compiled regex pattern for namespaces.
     */
    public static final Pattern NAMESPACE_PATTERN = Pattern.compile(NAMESPACE_REGEX);

    /**
     * The compiled regex pattern for entity names.
     */
    public static final Pattern ENTITY_NAME_PATTERN = Pattern.compile(ENTITY_NAME_REGEX);

    public static final String ID_PATTERN_MESSAGE = "The given identifier is not valid.";

    /**
     * The compiled regex pattern for namespaced entity IDs.
     */
    public static final Pattern ID_PATTERN = Pattern.compile(ID_REGEX);

    /**
     * Pattern that allows anything but {@link #CONTROL_CHARS}.
     */
    private static final Pattern NO_CONTROL_CHARS_PATTERN = Pattern.compile("^" + NO_CONTROL_CHARS + "+$");
    private static final String NO_CONTROL_CHARS_MESSAGE = "No control characters are allowed.";

    /**
     * The regex pattern a Subject has to conform to.
     */
    public static final PatternWithMessage
            SUBJECT_PATTERN = PatternWithMessage.of(NO_CONTROL_CHARS_PATTERN, NO_CONTROL_CHARS_MESSAGE);

    /**
     * The compiled regex pattern for policy resources.
     */
    public static final PatternWithMessage
            RESOURCE_PATTERN = PatternWithMessage.of(NO_CONTROL_CHARS_PATTERN, NO_CONTROL_CHARS_MESSAGE);

    /**
     * Wraps a compiled {@link Pattern} and an error message that can be used in an exception if the pattern did not
     * match.
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
