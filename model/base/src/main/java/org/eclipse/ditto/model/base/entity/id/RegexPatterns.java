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

public final class RegexPatterns {

    public static final String NAMESPACE_GROUP_NAME = "ns";

    public static final String ALLOWED_NAMESPACE_CHARACTERS_REGEX = "[a-zA-Z]\\w*+";
    public static final String ALLOWED_NAMESPACE_CHARACTERS_INCLUDING_DOT =
            "\\." + ALLOWED_NAMESPACE_CHARACTERS_REGEX;

    public static final String NAMESPACE_REGEX = "(?<" + NAMESPACE_GROUP_NAME + ">|(?:" +
            "(?:" + ALLOWED_NAMESPACE_CHARACTERS_REGEX + ")" +
            "(?:" + ALLOWED_NAMESPACE_CHARACTERS_INCLUDING_DOT + ")*+))";

    public static final String NAMESPACE_DELIMITER = ":";


    public static final String ENTITY_NAME_GROUP_NAME = "name";

    public static final String URL_ESCAPES = "%\\p{XDigit}{2}";
    public static final String ALLOWED_CHARACTERS_IN_NAME = "-\\w:@&=+,.!~*'_;<>";
    public static final String ALLOWED_CHARACTERS_IN_NAME_INCLUDING_DOLLAR = ALLOWED_CHARACTERS_IN_NAME + "$";

    public static final String URI_PATH_SEGMENT = "(?:[" + ALLOWED_CHARACTERS_IN_NAME + "]|" + URL_ESCAPES + ")";
    public static final String URI_PATH_SEGMENT_INCLUDING_DOLLAR =
            "(?:[" + ALLOWED_CHARACTERS_IN_NAME_INCLUDING_DOLLAR + "]|" + URL_ESCAPES + ")";
    /**
     * The regex pattern for an Entity Name. Has to be conform to
     * <a href="http://www.ietf.org/rfc/rfc2396.txt">RFC-2396</a>.
     */
    public static final String ENTITY_NAME_REGEX = "(?<" + ENTITY_NAME_GROUP_NAME +
            ">" + URI_PATH_SEGMENT + URI_PATH_SEGMENT_INCLUDING_DOLLAR + "*+)";


    /**
     * The regex pattern for an Entity ID.
     * Combines "namespace" pattern (java package notation + a colon) and "name" pattern.
     */
    public static final String ID_REGEX = NAMESPACE_REGEX + NAMESPACE_DELIMITER + ENTITY_NAME_REGEX;

    public static final Pattern NAMESPACE_PATTERN = Pattern.compile(NAMESPACE_REGEX);
    public static final Pattern ENTITY_NAME_PATTERN = Pattern.compile(ENTITY_NAME_REGEX);
    public static final Pattern ID_PATTERN = Pattern.compile(ID_REGEX);

    private RegexPatterns() {}
}
