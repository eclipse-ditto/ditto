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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

public class CborFuzzingTest {

    private final int INPUT_COUNT = 10000;
    private final int INPUT_LENGTH_MAX = 10;

    @Test
    public void fuzzingTest() throws IOException {
        for (byte[] bytes : generateInputs()) {
            testValue(bytes);
        }
    }


    private void testValue(byte[] array) throws IOException {
        try {
            CborFactory.readFrom(array);
        } catch (JsonParseException e) {
            // these exceptions are expected
        } catch (Exception e){
            System.out.println(BinaryToHexConverter.toHexString(array));
            throw e;
        }
    }

    private List<byte[]> generateInputs(){
        final Random random = new Random(generateSeed());
        final ArrayList<byte[]> inputs = new ArrayList<>(INPUT_COUNT);
        for (int i = 0; i < INPUT_COUNT; i++) {
            final int inputLength = random.nextInt(INPUT_LENGTH_MAX);
            final byte[] bytes = new byte[inputLength];
            random.nextBytes(bytes);
            inputs.add(bytes);
        }
        return inputs;
    }

    private long generateSeed(){
        final long seed = new Random().nextLong();
        System.out.println("seed for fuzzing test: " + seed);
        return seed;
    }

}
