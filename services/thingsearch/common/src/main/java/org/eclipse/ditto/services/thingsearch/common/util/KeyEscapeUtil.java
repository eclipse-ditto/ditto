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
package org.eclipse.ditto.services.thingsearch.common.util;

import static java.util.Objects.requireNonNull;

/**
 * Escapes strings to be usable as valid MongoDB keys.
 *
 * @see <a href="https://docs.mongodb.org/manual/faq/developers/#faq-dollar-sign-escaping">MongoDB Documentation</a>
 */
public final class KeyEscapeUtil {

    public static final String FAKE_DOLLAR = "\uFF04";
    public static final String FAKE_DOT = "\uFF0E";

    private KeyEscapeUtil() {
    }

    /**
     * Escapes the given String to be usable as MongoDB key.
     *
     * @param str the String
     * @return the escaped String
     */
    public static String escape(final String str) {
        requireNonNull(str);
        return str.replace("$", FAKE_DOLLAR).replace(".", FAKE_DOT);
    }
}
