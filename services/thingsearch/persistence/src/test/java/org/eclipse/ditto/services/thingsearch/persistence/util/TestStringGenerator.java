/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
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
