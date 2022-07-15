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

import org.eclipse.ditto.connectivity.api.ExternalMessage;

import akka.actor.ActorRef;

/**
 * This record bundles an {@link ExternalMessage} with the sender of this message.
 */
public record ExternalMessageWithSender(ExternalMessage externalMessage, ActorRef sender) {}
