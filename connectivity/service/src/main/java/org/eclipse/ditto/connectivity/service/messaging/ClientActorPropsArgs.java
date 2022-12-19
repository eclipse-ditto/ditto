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
package org.eclipse.ditto.connectivity.service.messaging;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;

import com.typesafe.config.Config;

import akka.actor.ActorRef;

/**
 * Arguments to create a client actor props object.
 *
 * @param connection the connection.
 * @param commandForwarderActor the actor used to send signals into the ditto cluster..
 * @param connectionActor the connectionPersistenceActor which creates this client.
 * @param dittoHeaders Ditto headers of the command that caused the client actors to be created.
 */
@Immutable
public record ClientActorPropsArgs(Connection connection, ActorRef commandForwarderActor, ActorRef connectionActor,
                                   DittoHeaders dittoHeaders, Config connectivityConfigOverwrites) {
}
