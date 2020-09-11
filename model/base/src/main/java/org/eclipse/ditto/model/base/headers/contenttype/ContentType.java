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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Parses a string as content-type and provides information about how Ditto should treat the payload based in its
 * content-type.
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

    /**
     * The well known content-type "application/json".
     */
    public static final ContentType APPLICATION_JSON = ContentType.of("application/json");

    private final String value;
    private final ParsingStrategy parsingStrategy;

    private ContentType(final String value, final ParsingStrategy parsingStrategy) {
        this.value = value;
        this.parsingStrategy = parsingStrategy;
    }

    /**
     * Parses the given contentTypeValue into an instance of {@link ContentType}.
     *
     * @param contentTypeValue the content-type header value.
     * @throws NullPointerException if {@code contentTypeValue} was {@code null}.
     * @return the new instance
     */
    public static ContentType of(final CharSequence contentTypeValue) {
        final String lowerCaseValue = checkNotNull(contentTypeValue, "contentTypeValue").toString()
                .toLowerCase();
        final String mediaType = lowerCaseValue.split(";")[0];
        final ParsingStrategy parsingStrategy;
        if (TEXT_PATTERN.matcher(mediaType).matches()) {
            parsingStrategy = ParsingStrategy.TEXT;
        } else if (JSON_PATTERN.matcher(mediaType).matches()) {
            parsingStrategy = ParsingStrategy.JSON;
        } else {
            parsingStrategy = ParsingStrategy.BINARY;
        }
        return new ContentType(lowerCaseValue, parsingStrategy);
    }

    /**
     * @return the strategy of how to parse the content-type.
     */
    public ParsingStrategy getParsingStrategy() {
        return parsingStrategy;
    }

    /**
     * @return the actual content-type string value.
     */
    public String getValue() {
        return value;
    }

    /**
     * @return whether this content-type is to be parsed as text.
     */
    public boolean isText() {
        return parsingStrategy == ParsingStrategy.TEXT;
    }

    /**
     * @return whether this content-type is to be parsed as JSON.
     */
    public boolean isJson() {
        return parsingStrategy == ParsingStrategy.JSON;
    }

    /**
     * @return whether this content-type is to be parsed as binary.
     */
    public boolean isBinary() {
        return parsingStrategy == ParsingStrategy.BINARY;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ContentType that = (ContentType) o;
        return value.equals(that.value) &&
                parsingStrategy == that.parsingStrategy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, parsingStrategy);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "value=" + value +
                ", parsingStrategy=" + parsingStrategy +
                "]";
    }

    /**
     * Known strategies of payload parsing in Ditto.
     */
    public enum ParsingStrategy {
        TEXT,
        JSON,
        BINARY
    }

}
