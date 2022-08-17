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
package org.eclipse.ditto.thingsearch.service.persistence.util;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Generates test strings.
 */
public final class TestStringGenerator {

    private TestStringGenerator() {
        throw new AssertionError();
    }

    /**
     * Creates a test string of the given {@code bytes} in bytes.
     *
     * @param bytes the bytes
     * @return the test string
     */
    public static String createStringOfBytes(final int bytes) {
        final String unicodeString = "ひらがな";
        final int bytesOfUnicodeString = unicodeString.getBytes(StandardCharsets.UTF_8).length;
        final int multiples = bytes / bytesOfUnicodeString;
        final int remainder = bytes % bytesOfUnicodeString;
        return Stream.concat(repeat(unicodeString, multiples), repeat("x", remainder))
                .collect(Collectors.joining());
    }

    private static Stream<String> repeat(final String element, final int number) {
        return IntStream.range(0, number).mapToObj(i -> element);
    }

}
