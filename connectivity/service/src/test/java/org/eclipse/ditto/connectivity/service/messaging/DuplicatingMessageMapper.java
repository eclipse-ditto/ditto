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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.connectivity.service.mapping.AbstractMessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.DittoMessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperConfiguration;
import org.eclipse.ditto.protocol.Adaptable;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Implementation of {@link org.eclipse.ditto.connectivity.service.mapping.MessageMapper} that always duplicates the incoming message.
 */
public final class DuplicatingMessageMapper extends AbstractMessageMapper {

    static final String ALIAS = "Duplicating";

    /**
     * The context representing this mapper
     */
    static final MappingContext CONTEXT = ConnectivityModelFactory.newMappingContext(ALIAS, Collections.emptyMap());

    private final MessageMapper delegate;
    private Long n;

    DuplicatingMessageMapper(final ActorSystem actorSystem, final Config config) {
        super(actorSystem, config);
        delegate = new DittoMessageMapper(actorSystem, config);
    }

    private DuplicatingMessageMapper(final DuplicatingMessageMapper copyFromMapper) {
        super(copyFromMapper);
        delegate = copyFromMapper.delegate;
    }

    @Override
    public void doConfigure(final Connection connection, final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        n = configuration.findProperty("n").map(Long::valueOf).orElse(2L);
    }

    @Override
    public String getAlias() {
        return ALIAS;
    }

    @Override
    public boolean isConfigurationMandatory() {
        return false;
    }

    @Override
    public MessageMapper createNewMapperInstance() {
        return new DuplicatingMessageMapper(this);
    }

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        return LongStream.range(0, n)
                .mapToObj(i -> delegate.map(message))
                .flatMap(Collection::stream)
                .toList();
    }

    @Override
    public DittoHeaders getAdditionalInboundHeaders(final ExternalMessage message) {
        return DittoHeaders.empty();
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        return LongStream.range(0, n)
                .mapToObj(i -> delegate.map(adaptable))
                .flatMap(Collection::stream)
                .toList();
    }

}
