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
package org.eclipse.ditto.services.thingsearch.persistence.util;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Generates test strings.
 */
public final class TestStringGenerator {

    private TestStringGenerator(){
        throw new AssertionError();
    }

    /**
     * Creates a test string of the given {@code length}.
     *
     * @param length the length
     * @return the test string
     */
    public static String createString(final int length) {
        return IntStream.range(0, length)
                .mapToObj(i -> "$")
                .collect(Collectors.joining());
    }
}
