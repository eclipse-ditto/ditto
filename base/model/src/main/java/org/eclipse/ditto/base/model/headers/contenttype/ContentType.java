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
package org.eclipse.ditto.base.model.headers.contenttype;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.eclipse.ditto.base.model.common.DittoConstants;

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
            "(application/((vnd\\.)?.+\\+)?(" + String.join("|", APPLICATION_TYPES_CONSIDERED_TO_BE_STRING) + "))$");

    private static final Pattern JSON_PATTERN = Pattern.compile("(application/((vnd\\.)?.+\\+)?json)");

    // application content type for JSON Merge Patch
    private static final String JSON_MERGE_PATCH = "application/merge-patch+json";

    /**
     * The well known content-type "application/json".
     */
    public static final ContentType APPLICATION_JSON = ContentType.of("application/json");

    /**
     * The content-type "application/tm+json" for WoT Thing Model JSON.
     * @since 2.4.0
     */
    public static final ContentType APPLICATION_TM_JSON = ContentType.of("application/tm+json");

    /**
     * The content-type "application/td+json" for WoT Thing Description JSON.
     * @since 2.4.0
     */
    public static final ContentType APPLICATION_TD_JSON = ContentType.of("application/td+json");

    /**
     * The content-type "application/merge-patch+json".
     */
    public static final ContentType APPLICATION_MERGE_PATCH_JSON = ContentType.of(JSON_MERGE_PATCH);

    private final String value;
    private final String mediaType;
    private final ParsingStrategy parsingStrategy;

    private ContentType(final String value, final String mediaType, final ParsingStrategy parsingStrategy) {
        this.value = value;
        this.mediaType = mediaType;
        this.parsingStrategy = parsingStrategy;
    }

    /**
     * Parses the given contentTypeValue into an instance of {@link org.eclipse.ditto.base.model.headers.contenttype.ContentType}.
     *
     * @param contentTypeValue the content-type header value.
     * @return the new instance
     * @throws NullPointerException if {@code contentTypeValue} was {@code null}.
     */
    public static ContentType of(final CharSequence contentTypeValue) {
        final String lowerCaseValue = checkNotNull(contentTypeValue, "contentTypeValue").toString()
                .toLowerCase();
        final String mediaType = lowerCaseValue.split(";")[0];
        final ParsingStrategy parsingStrategy;
        if (JSON_MERGE_PATCH.equals(mediaType)) {
            parsingStrategy = ParsingStrategy.JSON_MERGE_PATCH;
        } else if (TEXT_PATTERN.matcher(mediaType).matches()) {
            parsingStrategy = ParsingStrategy.TEXT;
        } else if (JSON_PATTERN.matcher(mediaType).matches()) {
            parsingStrategy = ParsingStrategy.JSON;
        } else {
            parsingStrategy = ParsingStrategy.BINARY;
        }
        return new ContentType(lowerCaseValue, mediaType, parsingStrategy);
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
     * @return whether this content-type matches the Ditto Protocol {@link org.eclipse.ditto.base.model.common.DittoConstants#DITTO_PROTOCOL_CONTENT_TYPE}.
     */
    public boolean isDittoProtocol() {
        return DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE.equals(mediaType);
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
     * @return whether this content-type is to be parsed as JSON Merge Patch.
     */
    public boolean isJsonMergePatch() {
        return JSON_MERGE_PATCH.equals(mediaType);
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
        JSON_MERGE_PATCH,
        BINARY
    }

}
