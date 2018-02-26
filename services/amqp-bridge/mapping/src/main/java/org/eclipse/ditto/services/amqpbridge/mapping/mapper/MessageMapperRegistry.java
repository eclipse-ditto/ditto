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

import java.util.Collection;
import java.util.Optional;

/**
 * Defines a collection of mappers with a fallback default mapper.
 */
public interface MessageMapperRegistry {

    /**
     * Returns a mapper with the supposed role of a fallback mapping strategy.
     * @return the default mapper
     */
    MessageMapper getDefaultMapper();

    /**
     * Returns a list of all available mappers excluding the default mapper
     * @return the mappers
     */
    Collection<MessageMapper> getMappers();

    /**
     * Searches a mapper for a specific content type.
     *
     * @param contentType the content type
     * @return the mapper if found
     */
    Optional<MessageMapper> findMapper(final String contentType);

    /**
     * Searches a mapper for a specific content type and returns the default mapper if none was found.
     *
     * @param contentType the content type
     * @return the selected mapper
     */
    default MessageMapper selectMapper(final String contentType) {
        return findMapper(contentType).orElse(getDefaultMapper());
    }
}
