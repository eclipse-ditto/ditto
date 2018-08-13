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

import java.util.Set;

import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Represent an outbound signal that was mapped to an external message.
 */
public class MappedOutboundSignal implements OutboundSignal.WithExternalMessage {

    private final OutboundSignal delegate;
    private final ExternalMessage externalMessage;

    MappedOutboundSignal(final OutboundSignal delegate, final ExternalMessage externalMessage) {
        this.delegate = delegate;
        this.externalMessage = externalMessage;
    }

    @Override
    public ExternalMessage getExternalMessage() {
        return externalMessage;
    }

    @Override
    public Signal<?> getSource() {
        return delegate.getSource();
    }

    @Override
    public Set<Target> getTargets() {
        return delegate.getTargets();
    }

}
