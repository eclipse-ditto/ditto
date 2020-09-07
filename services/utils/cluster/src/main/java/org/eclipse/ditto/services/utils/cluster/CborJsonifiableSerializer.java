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
package org.eclipse.ditto.services.utils.cluster;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.json.CborFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import akka.actor.ExtendedActorSystem;

/**
 * Serializer of Eclipse Ditto for Jsonifiables via CBOR-based {@code ditto-json}.
 */
public final class CborJsonifiableSerializer extends AbstractJsonifiableWithDittoHeadersSerializer {

    private static final int UNIQUE_IDENTIFIER = 656329405;

    private static final CborFactory CBOR_FACTORY;

    static {
        final ServiceLoader<CborFactory> sl = ServiceLoader.load(CborFactory.class);
        CBOR_FACTORY = StreamSupport.stream(sl.spliterator(), false)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("Could not lookup CborFactory ServiceLoader implementation"));
        if (!CBOR_FACTORY.isCborAvailable()) {
            throw new IllegalStateException("CborFactory was provided via ServiceLoader, " +
                    "however is not configured to handle CBOR");
        }
    }

    /**
     * Constructs a new {@code CborJsonifiableSerializer} object.
     *
     * @param actorSystem the ExtendedActorSystem to use in order to dynamically load mapping strategies in parent.
     */
    public CborJsonifiableSerializer(final ExtendedActorSystem actorSystem) {
        super(UNIQUE_IDENTIFIER, actorSystem, ManifestProvider.getInstance(), "CBOR");
    }

    @Override
    protected void serializeIntoByteBuffer(final JsonObject jsonObject, final ByteBuffer byteBuffer) throws IOException {
        CBOR_FACTORY.writeToByteBuffer(jsonObject, byteBuffer);
    }

    @Override
    protected JsonValue deserializeFromByteBuffer(final ByteBuffer byteBuffer) {
        return CBOR_FACTORY.readFrom(byteBuffer);
    }
}
