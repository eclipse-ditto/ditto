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

public class CborAvailabilityChecker {

    static final boolean CBOR_AVAILABLE = CborAvailabilityChecker.isCborAvailable();

    private CborAvailabilityChecker(){
        throw new AssertionError();
    }

    private static boolean isCborAvailable(){
        try {
            @SuppressWarnings({"squid:S1481", "squid:S1854"}) // used to determine availability of jackson-core at runtime
            JsonFactory jsonFactory = new JsonFactory();
            @SuppressWarnings({"squid:S1481", "squid:S1854"}) // used to determine availability of jackson-databind-cbor at runtime
            CBORFactory cborFactory = new CBORFactory();
        } catch (NoClassDefFoundError e){
            return false;
        }
        return true;
    }
}
