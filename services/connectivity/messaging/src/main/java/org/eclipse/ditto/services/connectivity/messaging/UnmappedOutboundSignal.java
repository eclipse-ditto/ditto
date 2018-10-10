/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.connectivity.messaging;

import java.util.Set;

import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Represents an outbound signal before it was mapped to an {@link org.eclipse.ditto.model.connectivity.ExternalMessage}.
 */
public class UnmappedOutboundSignal implements OutboundSignal {

    private final Signal<?> source;
    private final Set<Target> targets;

    public UnmappedOutboundSignal(final Signal<?> source, final Set<Target> targets) {
        this.source = source;
        this.targets = targets;
    }

    @Override
    public Signal<?> getSource() {
        return source;
    }

    @Override
    public Set<Target> getTargets() {
        return targets;
    }
}
