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

import java.util.Objects;
import java.util.Set;

import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Represents an outbound signal before it was mapped to an {@link ExternalMessage}.
 */
final class UnmappedOutboundSignal implements OutboundSignal {

    private final Signal<?> source;
    private final Set<Target> targets;

    UnmappedOutboundSignal(final Signal<?> source, final Set<Target> targets) {
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UnmappedOutboundSignal)) {
            return false;
        }
        final UnmappedOutboundSignal that = (UnmappedOutboundSignal) o;
        return Objects.equals(source, that.source) &&
                Objects.equals(targets, that.targets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, targets);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "source=" + source +
                ", targets=" + targets +
                "]";
    }
}
