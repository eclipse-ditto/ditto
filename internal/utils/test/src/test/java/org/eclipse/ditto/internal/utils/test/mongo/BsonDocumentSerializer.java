/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.test.mongo;

import java.nio.charset.StandardCharsets;

import org.bson.BsonDocument;

import akka.serialization.JSerializer;

/**
 * Serializer for BsonDocument for unit tests using the in-memory persistence plugin.
 */
public final class BsonDocumentSerializer extends JSerializer {

    @Override
    public Object fromBinaryJava(final byte[] bytes, final Class<?> manifest) {
        return BsonDocument.parse(new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public int identifier() {
        return 2114006166;
    }

    @Override
    public byte[] toBinary(final Object o) {
        final var bsonDocument = (BsonDocument) o;
        return bsonDocument.toJson().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean includeManifest() {
        return false;
    }
}
