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
package org.eclipse.ditto.services.connectivity.mapping;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.MappingContext;

/**
 * Instantiates {@link MessageMapper} and
 * {@link MessageMapperRegistry} instances from
 * {@link org.eclipse.ditto.model.connectivity.MappingContext} information.
 */
public interface MessageMapperFactory {

    /**
     * Creates an configures a {@link MessageMapper} instance of
     * the given context. It is possible, that there is no mapper available for a specific context.
     *
     * @param context the context
     * @return the mapper
     * @throws java.lang.NullPointerException if the context is null
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException if the configuration of
     * the {@code context} is invalid
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationFailedException if the configuration of
     * the {@code context} failed for a mapper specific reason
     */
    Optional<MessageMapper> mapperOf(MappingContext context);

    /**
     * Creates a {@link MessageMapperRegistry}. Mappers that are
     * not available will not be added to the registry.
     *
     * @param defaultContext the context used to instantiate the default mapper
     * @param context the contexts used to instantiate the registry mapper
     * @return the registry
     * @throws java.lang.NullPointerException if a parameters is null
     * @throws java.lang.IllegalArgumentException if the default mapper could not be instantiated
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException if the configuration of
     * the {@code context} is invalid
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationFailedException if the configuration of
     * the {@code context} failed for a mapper specific reason
     */
    MessageMapperRegistry registryOf(MappingContext defaultContext, @Nullable MappingContext context);
}
