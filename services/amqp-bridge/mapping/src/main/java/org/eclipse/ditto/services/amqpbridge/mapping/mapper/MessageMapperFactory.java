/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.model.amqpbridge.MappingContext;

/**
 * Instantiates {@link org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper} and
 * {@link org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapperRegistry} instances from
 * {@link org.eclipse.ditto.model.amqpbridge.MappingContext} information.
 */
public interface MessageMapperFactory {

    /**
     * Creates an configures a {@link org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper} instance of
     * the given context. It is possible, that there is no mapper available for a specific context.
     *
     * @param context the context
     * @return the mapper
     * @throws java.lang.NullPointerException if the context is null
     */
    Optional<MessageMapper> mapperOf(final MappingContext context);

    /**
     * Creates an configures multiple {@link org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper}
     * instancec of the given contexts. Mappers that are not available are excluded from the result list.
     *
     * @param contexts the contexts
     * @return the mappers that could be instantiated and configured
     * @throws java.lang.NullPointerException if the context is null
     */
    List<MessageMapper> mappersOf(final List<MappingContext> contexts);

    /**
     * Creates a {@link org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapperRegistry}. Mappers that are
     * not available will not be added to the registry.
     *
     * @param defaultContext the context used to instantiate the default mapper
     * @param contexts the contexts used to instantiate the registries mappers
     * @return the registry
     * @throws java.lang.NullPointerException if a parameters is null
     * @throws java.lang.IllegalArgumentException if the default mapper could not be instantiated
     */
    MessageMapperRegistry registryOf(final MappingContext defaultContext, final List<MappingContext> contexts);
}
