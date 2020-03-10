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
package org.eclipse.ditto.json;

import java.io.IOException;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Test;

public final class CborFuzzingTest {

    private static final int INPUT_COUNT = 10000;
    private static final int INPUT_LENGTH_MAX = 10;

    @Test
    public void fuzzingTest() {
        generateInputs().forEach(this::testValue);
    }

    private void testValue(final byte[] array) {
        try {
            CborFactory.readFrom(array);
        } catch (final JsonParseException e) {
            // these exceptions are expected
        } catch (final Exception e) {
            try {
                System.out.println(BinaryToHexConverter.toHexString(array));
            } catch (final IOException ioException) {
                System.err.println("Failed to convert to hex string");
            }
            throw e;
        }
    }

    private Stream<byte[]> generateInputs() {
        final Random random = new Random(generateSeed());
        return IntStream.range(0, INPUT_COUNT)
                .mapToObj(i -> {
                    final int inputLength = random.nextInt(INPUT_LENGTH_MAX);
                    final byte[] bytes = new byte[inputLength];
                    random.nextBytes(bytes);
                    return bytes;
                });
    }

    private long generateSeed() {
        return new Random().nextLong();
    }

}
