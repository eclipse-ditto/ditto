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
package org.eclipse.ditto.services.connectivity.mapping;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.MappingContext;

import akka.actor.ExtendedActorSystem;

/**
 * Interface for dynamic message mapper instantiation.
 */
@FunctionalInterface
public interface MessageMapperInstantiation {

    /**
     * Instantiates a message mapper.
     *
     * @param connectionId ID of the connection.
     * @param mappingContext the mapping context that configures the mapper.
     * @param actorSystem actor system in which the message mapper is created.
     * @return an instantiated message mapper according to the mapping context if instantiation is possible, or
     * {@code null} otherwise.
     */
    @Nullable
    MessageMapper apply(ConnectionId connectionId, MappingContext mappingContext, ExtendedActorSystem actorSystem);

}
