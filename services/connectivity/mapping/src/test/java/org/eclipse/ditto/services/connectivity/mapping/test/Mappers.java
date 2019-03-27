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
package org.eclipse.ditto.services.connectivity.mapping.test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperInstantiation;
import org.eclipse.ditto.services.connectivity.mapping.MessageMappers;

import akka.actor.ExtendedActorSystem;

/**
 * Mock factory to create test mappers.
 * Used via dynamic access in {@link org.eclipse.ditto.services.connectivity.mapping.MessageMapperFactoryTest}.
 */
@SuppressWarnings("unused")
public final class Mappers implements MessageMapperInstantiation {

    private final MessageMapperInstantiation defaultMappers = new MessageMappers();

    @Nullable
    @Override
    public MessageMapper apply(@Nonnull final String connectionId, @Nonnull final MappingContext mappingContext,
            @Nonnull final ExtendedActorSystem actorSystem) {

        return "test".equalsIgnoreCase(mappingContext.getMappingEngine())
                ? new MockMapper()
                : defaultMappers.apply(connectionId, mappingContext, actorSystem);
    }

}
