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

public class ContentType {

    // all types of application content types that are considered to be text (application/javascript, application/ecmascript)
    private static final List<String> APPLICATION_TYPES_CONSIDERED_TO_BE_STRING =
            Arrays.asList("javascript", "ecmascript");

    private static final Pattern TEXT_PATTERN = Pattern.compile("^(text/.*)|" +
            "(application/(vnd\\..+\\+)?(" + String.join("|", APPLICATION_TYPES_CONSIDERED_TO_BE_STRING) + "))$");

    private static final Pattern JSON_PATTERN = Pattern.compile("(application/(vnd\\..+\\+)?json)");

    public static final ContentType APPLICATION_JSON = ContentType.of("application/json");

    private final String value;
    private final boolean isText;
    private final boolean isJson;
    private final boolean isBinary;

    private ContentType(final String value, final boolean isText, final boolean isJson, final boolean isBinary) {
        this.value = value;
        this.isText = isText;
        this.isJson = isJson;
        this.isBinary = isBinary;
    }

    public static ContentType of(final String value) {
        final String lowerCaseValue = value.toLowerCase();
        final String mediaType = lowerCaseValue.split(";")[0];
        final boolean isText;
        final boolean isJson;
        final boolean isBinary;
        if (TEXT_PATTERN.matcher(mediaType).matches()) {
            isText = true;
            isJson = false;
            isBinary = false;
        } else if (JSON_PATTERN.matcher(mediaType).matches()) {
            isText = false;
            isJson = true;
            isBinary = false;
        } else {
            isText = false;
            isJson = false;
            isBinary = true;
        }
        return new ContentType(lowerCaseValue, isText, isJson, isBinary);
    }

    public boolean isText() {
        return isText;
    }

    public boolean isJson() {
        return isJson;
    }

    public boolean isBinary() {
        return isBinary;
    }

    public String getValue() {
        return value;
    }

}
