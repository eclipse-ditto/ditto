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
package org.eclipse.ditto.services.connectivity.messaging;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.connectivity.mapping.DittoMessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperConfiguration;
import org.eclipse.ditto.services.connectivity.mapping.PayloadMapper;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;

/**
 * Implementation of {@link MessageMapper} that delegates to
 * {@link org.eclipse.ditto.services.connectivity.mapping.DittoMessageMapper} and adds some headers to verify the
 * custom mapping was applied.
 */
@PayloadMapper(alias = AddHeaderMessageMapper.ALIAS)
public class AddHeaderMessageMapper implements MessageMapper {

    static final String ALIAS = "header";
    /**
     * The context representing this mapper
     */
    static final MappingContext CONTEXT = ConnectivityModelFactory.newMappingContext(ALIAS, Collections.emptyMap()
    );

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
    public void configure(final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        // ignore
    }

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        return delegate.map(message).stream().map(adaptable -> {
            final DittoHeadersBuilder modifiedHeaders = DittoHeaders.newBuilder(adaptable.getDittoHeaders());
            modifiedHeaders.putHeader(INBOUND_HEADER.getKey(), INBOUND_HEADER.getValue());
            return adaptable.setDittoHeaders(modifiedHeaders.build());
        }).collect(Collectors.toList());
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        return delegate.map(adaptable)
                .stream()
                .map(em -> em.withHeader(OUTBOUND_HEADER.getKey(), OUTBOUND_HEADER.getValue()))
                .collect(Collectors.toList());
    }
}
