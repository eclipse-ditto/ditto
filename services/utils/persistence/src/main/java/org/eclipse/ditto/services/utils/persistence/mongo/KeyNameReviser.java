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
package org.eclipse.ditto.services.utils.persistence.mongo;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

/**
 * This class can be used to revise the key name of a JSON object, i. e. to change its characters on the fly.
 */
@Immutable
final class KeyNameReviser implements Function<String, String> {

    static final char DOLLAR_CHAR = '$';
    static final char DOLLAR_UNICODE_CHAR = '\uFF04';
    static final char DOT_CHAR = '.';
    static final char DOT_UNICODE_CHAR = '\uFF0E';

    private final Function<String, String> replaceFunction;

    private KeyNameReviser(final Function<String, String> theReplaceFunction) {
        replaceFunction = theReplaceFunction;
    }

    /**
     * Returns an instance of {@code KeyNameReviser} with the specified replace function.
     *
     * @param replaceFunction the function (chain) to be used to revise a key name.
     * @return the instance.
     * @throws NullPointerException if {@code replaceFunction} is {@code null}.
     */
    public static KeyNameReviser getInstance(final Function<String, String> replaceFunction) {
        return new KeyNameReviser(checkNotNull(replaceFunction, "replace function"));
    }

    /**
     * Returns an instance of {@code KeyNameReviser} which replaces dot (<code>"{@value #DOT_CHAR}"</code>) and dollar
     * (<code>"{@value #DOLLAR_CHAR}"</code>) characters in the name of a JSON key with their unicode counterparts.
     *
     * @return the instance.
     */
    public static KeyNameReviser escapeProblematicPlainChars() {
        return getInstance(replace(DOT_CHAR, DOT_UNICODE_CHAR).andThen(replace(DOLLAR_CHAR, DOLLAR_UNICODE_CHAR)));
    }

    /**
     * Returns an instance of {@code KeyNameReviser} which replaces unicode dollar and dot characters in the name of a
     * JSON key with dollar (<code>"{@value #DOLLAR_CHAR}"</code>) and dot (<code>"{@value #DOT_CHAR}"</code>).
     *
     * @return the instance.
     */
    public static KeyNameReviser decodeKnownUnicodeChars() {
        return getInstance(replace(DOLLAR_UNICODE_CHAR, DOLLAR_CHAR).andThen(replace(DOT_UNICODE_CHAR, DOT_CHAR)));
    }

    private static Function<String, String> replace(final char oldChar, final char charNewChar) {
        return s -> s.replace(oldChar, charNewChar);
    }

    @Override
    public String apply(final String keyName) {
        checkNotNull(keyName, "key name to be revised");
        return replaceFunction.apply(keyName);
    }

}
