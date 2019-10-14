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

import static org.eclipse.ditto.services.connectivity.messaging.FaultyMessageMapper.ALIAS;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperConfiguration;
import org.eclipse.ditto.services.connectivity.mapping.PayloadMapper;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;

/**
 * Implementation of {@link MessageMapper} that always throws an exception.
 */
@PayloadMapper(alias = ALIAS)
public class FaultyMessageMapper implements MessageMapper {

    static final String ALIAS = "faulty";

    /**
     * The context representing this mapper
     */
    static final MappingContext CONTEXT = ConnectivityModelFactory.newMappingContext(ALIAS, Collections.emptyMap());

    @Override
    public String getId() {
        return "faulty";
    }

    @Override
    public void configure(final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        // ignore
    }

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        throw new IllegalStateException("inbound mapping failed");
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        throw new IllegalStateException("outbound mapping failed");
    }
}
