/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.mapping.javascript;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;

/**
 * The default mapping for incoming messages that maps messages from Ditto protocol format.
 */
public class DefaultIncomingMapping implements MappingFunction<ExternalMessage, Optional<Adaptable>> {

    private static DefaultIncomingMapping INSTANCE = new DefaultIncomingMapping();

    private DefaultIncomingMapping() {
    }

    static DefaultIncomingMapping get() {
        return INSTANCE;
    }

    @Override
    public Optional<Adaptable> apply(final ExternalMessage message) {
        return Optional.ofNullable(
                message.getTextPayload()
                        .orElseGet(() -> message.getBytePayload()
                                .map(b -> StandardCharsets.UTF_8.decode(b).toString())
                                .orElse(null))
        ).map(plainString -> DittoJsonException.wrapJsonRuntimeException(() -> {
            final JsonObject jsonObject = JsonFactory.readFrom(plainString).asObject();
            return ProtocolFactory.jsonifiableAdaptableFromJson(jsonObject);
        }));
    }
}
