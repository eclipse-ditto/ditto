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
package org.eclipse.ditto.services.utils.cluster;

import java.nio.ByteBuffer;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import akka.actor.ExtendedActorSystem;

/**
 * Serializer for Commands and Events of Eclipse Ditto.
 */
@NotThreadSafe
public final class JsonJsonifiableSerializer extends AbstractJsonifiableWithDittoHeadersSerializer {

    private static final int UNIQUE_IDENTIFIER = 784456217;

    /**
     * Constructs a new {@code JsonifiableSerializer} object.
     */
    public JsonJsonifiableSerializer(final ExtendedActorSystem actorSystem) {
        super(UNIQUE_IDENTIFIER, actorSystem, ManifestProvider.getInstance(), "JSON");
    }

    @Override
    protected void serializeIntoByteBuffer(final JsonObject jsonObject, final ByteBuffer byteBuffer) {
        final String jsonStr = jsonObject.toString();
        byteBuffer.put(CHARSET.encode(jsonStr));
    }

    @Override
    protected JsonValue deserializeFromByteBuffer(final ByteBuffer byteBuffer) {
        String json = CHARSET.decode(byteBuffer).toString();
        return JsonFactory.readFrom(json);
    }
}
