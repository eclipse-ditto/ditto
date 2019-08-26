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

    private static final DefaultIncomingMapping INSTANCE = new DefaultIncomingMapping();

    private DefaultIncomingMapping() {
    }

    static DefaultIncomingMapping get() {
        return INSTANCE;
    }

    @Override
    public Optional<Adaptable> apply(final ExternalMessage message) {
        return getPlainStringPayload(message).map(plainString -> DittoJsonException.wrapJsonRuntimeException(() -> {
            final JsonObject jsonObject = JsonFactory.readFrom(plainString).asObject();
            return ProtocolFactory.jsonifiableAdaptableFromJson(jsonObject);
        }));
    }

    private static Optional<String> getPlainStringPayload(final ExternalMessage message) {
        final String plainString;
        if (message.getTextPayload().isPresent()) {
            plainString = message.getTextPayload().get();
        } else {
            plainString = message.getBytePayload()
                    .map(b -> StandardCharsets.UTF_8.decode(b).toString())
                    .orElse(null);
        }
        return Optional.ofNullable(plainString);
    }
}
