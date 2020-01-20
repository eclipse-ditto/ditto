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

import org.eclipse.ditto.json.CborFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import akka.actor.ExtendedActorSystem;

public final class CborJsonifiableSerializer extends AbstractJsonifiableWithDittoHeadersSerializer {

    private static final int UNIQUE_IDENTIFIER = 656329405;

    /**
     * Constructs a new {@code CborJsonifiableSerializer} object.
     */
    public CborJsonifiableSerializer(final ExtendedActorSystem actorSystem) {
        super(UNIQUE_IDENTIFIER, actorSystem, ManifestProvider.getInstance(), "CBOR");
    }

    @Override
    protected void serializeIntoByteBuffer(final JsonObject jsonObject, final ByteBuffer byteBuffer) throws IOException {
        CborFactory.writeToByteBuffer(jsonObject, byteBuffer);
    }

    @Override
    protected JsonValue deserializeFromByteBuffer(ByteBuffer byteBuffer) {
        return CborFactory.readFrom(byteBuffer);
    }
}
