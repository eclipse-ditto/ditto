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

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

/**
 * CborFactory which in fact does not provide CBOR capabilities, throws UnsupportedOperationException for all specific
 * interface implementations except for {@link #isCborAvailable()} which it returns {@code false} for.
 */
final class NoopCborFactory implements CborFactory {

    private static final UnsupportedOperationException UNSUPPORTED_OPERATION_EXCEPTION =
            new UnsupportedOperationException(
                    "NoopCborFactory does not provide a functional CborFactory implementation");

    @Override
    public boolean isCborAvailable() {
        return false;
    }

    @Override
    public JsonValue readFrom(final byte[] bytes) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public JsonValue readFrom(final byte[] bytes, final int offset, final int length) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public JsonValue readFrom(final ByteBuffer byteBuffer) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public byte[] toByteArray(final JsonValue jsonValue) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public ByteBuffer toByteBuffer(final JsonValue jsonValue) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void writeToByteBuffer(final JsonValue jsonValue, final ByteBuffer byteBuffer) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public byte[] createCborRepresentation(final Map<String, JsonField> jsonFieldMap, final int guessedSerializedSize) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public byte[] createCborRepresentation(final List<JsonValue> list, final int guessedSerializedSize) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }
}
