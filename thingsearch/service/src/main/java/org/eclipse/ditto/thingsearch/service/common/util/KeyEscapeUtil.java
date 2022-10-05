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
package org.eclipse.ditto.thingsearch.service.common.util;

import static java.util.Objects.requireNonNull;

/**
 * Escapes strings to be usable as valid MongoDB keys.
 *
 * @see <a href="https://docs.mongodb.org/manual/faq/developers/#faq-dollar-sign-escaping">MongoDB Documentation</a>
 */
public final class KeyEscapeUtil {

    private static final String FAKE_TILDA = "~0";
    private static final String FAKE_DOLLAR = "~1";

    public static final String FAKE_DOT = "~2";

    private KeyEscapeUtil() {}

    /**
     * Escapes the given String to be usable as MongoDB key.
     *
     * @param str the String
     * @return the escaped String
     */
    public static String escape(final String str) {
        requireNonNull(str);
        return str.replace("~", FAKE_TILDA)
                .replace("$", FAKE_DOLLAR)
                .replace(".", FAKE_DOT);
    }

}
