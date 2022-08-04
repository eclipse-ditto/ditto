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
package org.eclipse.ditto.connectivity.service.messaging;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.mapping.DittoMessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperConfiguration;
import org.eclipse.ditto.connectivity.service.mapping.PayloadMapper;
import org.eclipse.ditto.protocol.Adaptable;

import akka.actor.ActorSystem;

/**
 * Implementation of {@link org.eclipse.ditto.connectivity.service.mapping.MessageMapper} that delegates to
 * {@link org.eclipse.ditto.connectivity.service.mapping.DittoMessageMapper} and adds some headers to verify the
 * custom mapping was applied.
 */
@PayloadMapper(alias = AddHeaderMessageMapper.ALIAS)
public final class AddHeaderMessageMapper implements MessageMapper {

    static final String ALIAS = "header";

    /**
     * The context representing this mapper
     */
    static final MappingContext CONTEXT = ConnectivityModelFactory.newMappingContext(ALIAS, Collections.emptyMap());

    static final Map.Entry<String, String> INBOUND_HEADER =
            new AbstractMap.SimpleImmutableEntry<>("inbound", "mapping");
    static final Map.Entry<String, String> OUTBOUND_HEADER =
            new AbstractMap.SimpleImmutableEntry<>("outbound", "mapping");

    private final MessageMapper delegate = new DittoMessageMapper();

    @Override
    public String getId() {
        return "header";
    }

    @Override
    public Collection<String> getContentTypeBlocklist() {
        return Collections.emptyList();
    }

    @Override
    public void configure(final Connection connection, final ConnectivityConfig connectivityConfig,
            final MessageMapperConfiguration configuration, final ActorSystem actorSystem) {
        // ignore
    }

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        return delegate.map(message).stream().map(adaptable -> {
            final DittoHeadersBuilder<?, ?> modifiedHeaders = DittoHeaders.newBuilder(adaptable.getDittoHeaders());
            modifiedHeaders.putHeader(INBOUND_HEADER.getKey(), INBOUND_HEADER.getValue());
            return adaptable.setDittoHeaders(modifiedHeaders.build());
        }).toList();
    }

    @Override
    public DittoHeaders getAdditionalInboundHeaders(final ExternalMessage message) {
        return DittoHeaders.empty();
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        return delegate.map(adaptable)
                .stream()
                .map(em -> em.withHeader(OUTBOUND_HEADER.getKey(), OUTBOUND_HEADER.getValue()))
                .toList();
    }

    @Override
    public Map<String, String> getIncomingConditions() {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, String> getOutgoingConditions() {
        return Collections.emptyMap();
    }

}
