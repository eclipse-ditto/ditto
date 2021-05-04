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

import javax.annotation.Nullable;

import org.atteo.classindex.IndexSubclasses;
import org.eclipse.ditto.connectivity.model.ConnectionId;

import akka.actor.ExtendedActorSystem;

/**
 * Interface for wrapping an existing message mapper after creation.
 */
@FunctionalInterface
@IndexSubclasses
public interface MessageMapperExtension {

    /**
     * Instantiates a message mapper.
     *
     * @param connectionId ID of the connection.
     * @param mapper the mapper that can be extended or wrapped.
     * @param actorSystem actor system in which the message mapper is created.
     * @return an instantiated message mapper according to the mapping context if instantiation is possible, or
     * {@code null} otherwise.
     */
    @Nullable
    MessageMapper apply(ConnectionId connectionId, MessageMapper mapper, ExtendedActorSystem actorSystem);

}
