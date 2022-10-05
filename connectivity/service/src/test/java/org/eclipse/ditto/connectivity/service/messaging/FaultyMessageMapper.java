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

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.connectivity.service.mapping.AbstractMessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperConfiguration;
import org.eclipse.ditto.protocol.Adaptable;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Implementation of {@link org.eclipse.ditto.connectivity.service.mapping.MessageMapper} that always throws an exception.
 */
public final class FaultyMessageMapper extends AbstractMessageMapper {

    static final String ALIAS = "Faulty";

    /**
     * The context representing this mapper
     */
    static final MappingContext CONTEXT = ConnectivityModelFactory.newMappingContext(ALIAS, Collections.emptyMap());

    FaultyMessageMapper(final ActorSystem actorSystem, final Config config) {
        super(actorSystem, config);
    }

    private FaultyMessageMapper(final FaultyMessageMapper copyFromMapper) {
        super(copyFromMapper);
    }

    @Override
    public void doConfigure(final Connection connection, final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        // ignore
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
        return new FaultyMessageMapper(this);
    }

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        throw new IllegalStateException("inbound mapping failed");
    }

    @Override
    public DittoHeaders getAdditionalInboundHeaders(final ExternalMessage message) {
        return DittoHeaders.empty();
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        throw new IllegalStateException("outbound mapping failed");
    }
}
