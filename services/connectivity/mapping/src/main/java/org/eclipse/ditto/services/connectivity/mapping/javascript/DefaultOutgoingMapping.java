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

import java.util.Optional;

import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageBuilder;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;

/**
 * The default mapping for outgoing messages that maps to Ditto protocol format.
 */
public class DefaultOutgoingMapping implements MappingFunction<Adaptable, Optional<ExternalMessage>> {

    private static final DefaultOutgoingMapping INSTANCE = new DefaultOutgoingMapping();

    private DefaultOutgoingMapping() {
    }

    static DefaultOutgoingMapping get() {
        return INSTANCE;
    }

    @Override
    public Optional<ExternalMessage> apply(final Adaptable adaptable) {
        final JsonifiableAdaptable jsonifiableAdaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);
        final ExternalMessageBuilder messageBuilder = ExternalMessageFactory.newExternalMessageBuilder(
                adaptable.getHeaders().orElseGet(adaptable::getDittoHeaders))
                        .withTopicPath(adaptable.getTopicPath());
        messageBuilder.withAdditionalHeaders(ExternalMessage.CONTENT_TYPE_HEADER,
                DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
        messageBuilder.withText(jsonifiableAdaptable.toJsonString());
        return Optional.of(messageBuilder.build());
    }

}
