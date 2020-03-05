/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

/**
 * Package internal util to determine whether CBOR is available to use for serialization.
 */
final class CborAvailabilityChecker {

    private static final boolean CBOR_AVAILABLE = CborAvailabilityChecker.calculateCborAvailable();

    private CborAvailabilityChecker() {
        throw new AssertionError();
    }

    /**
     * Determines whether the libraries providing CBOR serializations are available (classes can be loaded).
     *
     * @return {@code true} when CBOR is available and can be used for serialization.
     */
    static boolean isCborAvailable() {
        return CBOR_AVAILABLE;
    }

    private static boolean calculateCborAvailable() {
        try {
            // used to determine availability of jackson-core at runtime
            new JsonFactory();
            // used to determine availability of jackson-databind-cbor at runtime
            new CBORFactory();
        } catch (final NoClassDefFoundError e) {
            return false;
        }
        return true;
    }
}
