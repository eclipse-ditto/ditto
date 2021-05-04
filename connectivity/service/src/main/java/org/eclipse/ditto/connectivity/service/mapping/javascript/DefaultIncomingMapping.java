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
package org.eclipse.ditto.connectivity.service.mapping.javascript;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.connectivity.api.ExternalMessage;

/**
 * The default mapping for incoming messages that maps messages from Ditto protocol format.
 */
public final class DefaultIncomingMapping implements MappingFunction<ExternalMessage, List<Adaptable>> {

    private static final DefaultIncomingMapping INSTANCE = new DefaultIncomingMapping();

    private DefaultIncomingMapping() {
    }

    static DefaultIncomingMapping get() {
        return INSTANCE;
    }

    @Override
    public List<Adaptable> apply(final ExternalMessage message) {
        return DittoJsonException.wrapJsonRuntimeException(() -> getPlainStringPayload(message)
                .map(JsonFactory::readFrom)
                .map(JsonValue::asObject)
                .map(ProtocolFactory::jsonifiableAdaptableFromJson))
                .map(Adaptable.class::cast)
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    private static Optional<String> getPlainStringPayload(final ExternalMessage message) {
        final String plainString;
        final Optional<String> textPayloadOptional = message.getTextPayload();
        plainString = textPayloadOptional.orElseGet(() -> message.getBytePayload()
                .map(b -> StandardCharsets.UTF_8.decode(b).toString())
                .orElse(null));
        return Optional.ofNullable(plainString);
    }
}
