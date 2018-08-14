/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.connectivity.messaging;

import akka.actor.AbstractActor;

/**
 * Base class for publisher actors. Holds the map of configured targets.
 *
 * @param <T> the type of targets for this actor
 */
public abstract class BasePublisherActor<T extends PublishTarget> extends AbstractActor {

    protected boolean isResponseOrError(final OutboundSignal.WithExternalMessage outboundSignal) {
        return (outboundSignal.getExternalMessage().isResponse()
                || outboundSignal.getExternalMessage().isError());
    }

    protected abstract T toPublishTarget(final String address);
}
