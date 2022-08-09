/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.connectivity.model.ConnectionId;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

public class NoOpMessageMapperExtension implements MessageMapperExtension {

    @SuppressWarnings("unused")
    public NoOpMessageMapperExtension(final ActorSystem actorSystem, final Config config) {
        // No-Op because Extensions need to have constructor accepting the actorSystem.
    }

    @Nullable
    @Override
    public MessageMapper apply(final ConnectionId connectionId, final MessageMapper mapper) {
        return mapper;
    }

}
