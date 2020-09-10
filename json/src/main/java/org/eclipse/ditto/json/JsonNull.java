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
package org.eclipse.ditto.json;

import java.io.IOException;

/**
 * This is a interface to mark all classes that represent a JSON {@code null} value somehow.
 */
interface JsonNull extends JsonValue {

    @Override
    default void writeValue(final SerializationContext serializationContext) throws IOException {
        serializationContext.writeNull();
    }

    @Override
    default long getUpperBoundForStringSize() {
        return 4; // "null"
    }
}
