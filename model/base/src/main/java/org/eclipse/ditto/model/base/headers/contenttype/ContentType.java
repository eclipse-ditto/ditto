/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.base.headers.contenttype;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses a string as content type and provides information about how ditto should treat the payload based in its
 * content type.
 *
 * @since 1.3.0
 */
public final class ContentType {

    // all types of application content types that are considered to be text (application/javascript, application/ecmascript)
    private static final List<String> APPLICATION_TYPES_CONSIDERED_TO_BE_STRING =
            Arrays.asList("javascript", "ecmascript");

    private static final Pattern TEXT_PATTERN = Pattern.compile("^(text/.*)|" +
            "(application/(vnd\\..+\\+)?(" + String.join("|", APPLICATION_TYPES_CONSIDERED_TO_BE_STRING) + "))$");

    private static final Pattern JSON_PATTERN = Pattern.compile("(application/(vnd\\..+\\+)?json)");

    public static final ContentType APPLICATION_JSON = ContentType.of("application/json");

    private final String value;
    private final ParsingStrategyType parsingStrategy;

    private ContentType(final String value, final ParsingStrategyType parsingStrategy) {
        this.value = value;
        this.parsingStrategy = parsingStrategy;
    }

    /**
     * Parses the given content type value into an instance of {@link ContentType}.
     *
     * @param value the content-type header value.
     * @return the new instance
     */
    public static ContentType of(final String value) {
        final String lowerCaseValue = value.toLowerCase();
        final String mediaType = lowerCaseValue.split(";")[0];
        final ParsingStrategyType parsingStrategy;
        if (TEXT_PATTERN.matcher(mediaType).matches()) {
            parsingStrategy = ParsingStrategyType.TEXT;
        } else if (JSON_PATTERN.matcher(mediaType).matches()) {
            parsingStrategy = ParsingStrategyType.JSON;
        } else {
            parsingStrategy = ParsingStrategyType.BINARY;
        }
        return new ContentType(lowerCaseValue, parsingStrategy);
    }

    public ParsingStrategyType getParsingStrategyType() {
        return parsingStrategy;
    }

    public String getValue() {
        return value;
    }

    public boolean isText() {
        return parsingStrategy == ParsingStrategyType.TEXT;
    }

    public boolean isJson() {
        return parsingStrategy == ParsingStrategyType.JSON;
    }

    public boolean isBinary() {
        return parsingStrategy == ParsingStrategyType.BINARY;
    }

    /**
     * Known types of payload parsing in ditto.
     *
     * @since 1.3.0
     */
    public enum ParsingStrategyType {
        TEXT,
        JSON,
        BINARY;
    }

}
