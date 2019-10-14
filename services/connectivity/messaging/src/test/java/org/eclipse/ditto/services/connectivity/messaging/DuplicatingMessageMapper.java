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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

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
 * Implementation of {@link MessageMapper} that always duplicates the incoming message.
 */
@PayloadMapper(alias = DuplicatingMessageMapper.ALIAS)
public class DuplicatingMessageMapper implements MessageMapper {

    static final String ALIAS = "duplicating";

    /**
     * The context representing this mapper
     */
    static final MappingContext CONTEXT = ConnectivityModelFactory.newMappingContext(
            ALIAS, Collections.emptyMap()
    );
    private MessageMapper delegate = new DittoMessageMapper();
    private Long n;

    @Override
    public String getId() {
        return "duplicating";
    }

    @Override
    public void configure(final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        n = configuration.findProperty("n").map(Long::valueOf).orElse(2L);
    }

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        return LongStream.range(0, n)
                .mapToObj(i -> delegate.map(message))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        return LongStream.range(0, n)
                .mapToObj(i -> delegate.map(adaptable))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }
}
