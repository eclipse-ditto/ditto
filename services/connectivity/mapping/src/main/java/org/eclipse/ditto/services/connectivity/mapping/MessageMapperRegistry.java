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
     * Returns the mapper to use for mapping.
     * @return the mapper
     */
    Optional<MessageMapper> getMapper();

}
