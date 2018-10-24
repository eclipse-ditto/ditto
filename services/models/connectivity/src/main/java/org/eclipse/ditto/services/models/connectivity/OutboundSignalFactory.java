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
package org.eclipse.ditto.services.models.connectivity;

import java.util.Set;

import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Creates instances of {@link OutboundSignal}.
 */
public class OutboundSignalFactory {

    public static OutboundSignal newOutboundSignal(final Signal<?> signal, final Set<Target> targets) {
        return new UnmappedOutboundSignal(signal, targets);
    }

    public static OutboundSignal newMappedOutboundSignal(final OutboundSignal signal,
            final ExternalMessage externalMessage) {
        return new MappedOutboundSignal(signal, externalMessage);
    }

    private OutboundSignalFactory() {
        throw new AssertionError();
    }
}
