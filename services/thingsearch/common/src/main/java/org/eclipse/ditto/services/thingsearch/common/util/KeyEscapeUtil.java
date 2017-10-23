/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
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
