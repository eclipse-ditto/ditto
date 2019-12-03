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

import java.io.Reader;
import java.nio.ByteBuffer;

//TODO: move these Methods to JsonFactory

public final class CborFactory {

    /*
     * This utility class is not meant to be instantiated.
     */
    private CborFactory(){
        throw new AssertionError();
    }

    public static JsonValue readFrom(final byte[] bytes){
        // TODO implement
        return null;
    }

    public static JsonValue readFrom(final byte[] bytes, int offset, int length){
        // TODO implement
        return null;
    }

    public static JsonValue readFrom(ByteBuffer byteBuffer){
        // TODO implement
        return null;
    }

    public static JsonValue readFrom(Reader reader){
        // TODO implement
        return null;
    }

    public static byte[] toByteArray(JsonValue jsonValue){
        // TODO implement by calling jsonValue.toByteArray with an appropriate Generator
        return null;
    }

    public static ByteBuffer toByteBuffer(JsonValue jsonValue){
        // TODO implement by calling jsonValue.toByteBuffer with an appropriate Generator
        return null;
    }

    // TODO: support the equivalent of Reader

}
