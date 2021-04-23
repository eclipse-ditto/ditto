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

import static org.eclipse.ditto.base.model.common.DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageBuilder;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;

/**
 * The default mapping for outgoing messages that maps to Ditto protocol format.
 */
public class DefaultOutgoingMapping implements MappingFunction<Adaptable, List<ExternalMessage>> {

    private static final DefaultOutgoingMapping INSTANCE = new DefaultOutgoingMapping();

    private DefaultOutgoingMapping() {
    }

    static DefaultOutgoingMapping get() {
        return INSTANCE;
    }

    @Override
    public List<ExternalMessage> apply(final Adaptable adaptable) {
        final JsonifiableAdaptable jsonifiableAdaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);
        final ExternalMessageBuilder messageBuilder = ExternalMessageFactory
                .newExternalMessageBuilder(adaptable.getDittoHeaders())
                .withTopicPath(adaptable.getTopicPath());
        messageBuilder.withAdditionalHeaders(ExternalMessage.CONTENT_TYPE_HEADER, DITTO_PROTOCOL_CONTENT_TYPE);
        messageBuilder.withText(jsonifiableAdaptable.toJsonString());
        return Collections.singletonList(messageBuilder.build());
    }

}
