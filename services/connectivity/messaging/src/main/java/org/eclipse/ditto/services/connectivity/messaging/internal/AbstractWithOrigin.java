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
package org.eclipse.ditto.services.connectivity.messaging.internal;

import java.util.Optional;

import javax.annotation.Nullable;

import akka.actor.ActorRef;

/**
 * Abstract base implementation for {@link WithOrigin}.
 */
public abstract class AbstractWithOrigin implements WithOrigin {

    @Nullable private final ActorRef origin;

    protected AbstractWithOrigin(@Nullable final ActorRef origin) {
        this.origin = origin;
    }

    @Override
    public Optional<ActorRef> getOrigin() {
        return Optional.ofNullable(origin);
    }
}
