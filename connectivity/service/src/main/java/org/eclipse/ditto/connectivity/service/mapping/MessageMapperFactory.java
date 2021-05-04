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
package org.eclipse.ditto.connectivity.service.mapping;

import java.util.Optional;

import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.model.PayloadMappingDefinition;

/**
 * Instantiates {@link MessageMapper} and
 * {@link MessageMapperRegistry} instances from
 * {@link org.eclipse.ditto.connectivity.model.MappingContext} information.
 */
public interface MessageMapperFactory {

    /**
     * Creates an configures a {@link MessageMapper} instance of
     * the given context. It is possible, that there is no mapper available for a specific context.
     *
     * @param mapperId the id of this mapper
     * @param context the mapping context containing the type and configuration of the mapper
     * @return the mapper
     * @throws java.lang.NullPointerException if the context is null
     * @throws org.eclipse.ditto.connectivity.model.MessageMapperConfigurationInvalidException if the configuration of
     * the {@code context} is invalid
     * @throws org.eclipse.ditto.connectivity.model.MessageMapperConfigurationFailedException if the configuration of
     * the {@code context} failed for a mapper specific reason
     */
    Optional<MessageMapper> mapperOf(String mapperId, MappingContext context);

    /**
     * Creates a {@link MessageMapperRegistry}. Mappers that are not available will not be added to the registry.
     *
     * @param defaultContext the context used to instantiate the default mapper
     * @param mappingDefinitions the mapping definitions used to instantiate the registry mapper
     * @return the registry
     * @throws java.lang.NullPointerException if a parameters is null
     * @throws java.lang.IllegalArgumentException if the default mapper could not be instantiated
     * @throws org.eclipse.ditto.connectivity.model.MessageMapperConfigurationInvalidException if the configuration of
     * the {@code context} is invalid
     * @throws org.eclipse.ditto.connectivity.model.MessageMapperConfigurationFailedException if the configuration of
     * the {@code context} failed for a mapper specific reason
     */
    MessageMapperRegistry registryOf(MappingContext defaultContext, PayloadMappingDefinition mappingDefinitions);
}
