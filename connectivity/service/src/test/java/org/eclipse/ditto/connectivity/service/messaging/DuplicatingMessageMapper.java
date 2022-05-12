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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;

import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.connectivity.service.mapping.AbstractMessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.DittoMessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperConfiguration;
import org.eclipse.ditto.connectivity.service.mapping.PayloadMapper;
import org.eclipse.ditto.protocol.Adaptable;

/**
 * Implementation of {@link org.eclipse.ditto.connectivity.service.mapping.MessageMapper} that always duplicates the incoming message.
 */
@PayloadMapper(alias = DuplicatingMessageMapper.ALIAS)
public final class DuplicatingMessageMapper extends AbstractMessageMapper {

    static final String ALIAS = "duplicating";

    /**
     * The context representing this mapper
     */
    static final MappingContext CONTEXT = ConnectivityModelFactory.newMappingContext(ALIAS, Collections.emptyMap());

    private final MessageMapper delegate = new DittoMessageMapper();
    private Long n;

    @Override
    public void doConfigure(final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        n = configuration.findProperty("n").map(Long::valueOf).orElse(2L);
    }

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        return LongStream.range(0, n)
                .mapToObj(i -> delegate.map(message))
                .flatMap(Collection::stream)
                .toList();
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        return LongStream.range(0, n)
                .mapToObj(i -> delegate.map(adaptable))
                .flatMap(Collection::stream)
                .toList();
    }

}
