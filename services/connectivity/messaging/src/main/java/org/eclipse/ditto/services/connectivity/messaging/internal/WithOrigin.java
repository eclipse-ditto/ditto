/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.internal;

import java.util.Optional;

import akka.actor.ActorRef;

/**
 * Messaging internal interface for all messages containing an {@link ActorRef} origin.
 */
public interface WithOrigin {

    /**
     * @return the optional ActorRef where this message originated from.
     */
    Optional<ActorRef> getOrigin();
}
