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
package org.eclipse.ditto.services.gateway.proxy.actors;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.distributedcache.actors.ReadConsistency;

/**
 * A message for looking up an Enforcer (Policy- or ACL-Enforcer). The passed in {@link LookupContext} contains both the
 * initial sender and the recipient of the {@link LookupEnforcerResponse} message.
 */
@Immutable
public final class LookupEnforcer {

    private final String signalId;
    private final LookupContext<?> context;
    private final ReadConsistency readConsistency;

    /**
     * Constructs a new {@code LookupEnforcer} object.
     * 
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code signalId} is empty.
     */
    public LookupEnforcer(final CharSequence signalId, final LookupContext<?> context,
            final ReadConsistency readConsistency) {
        this.signalId = argumentNotEmpty(signalId, "ID").toString();
        this.context = checkNotNull(context, "Lookup Context");
        this.readConsistency = checkNotNull(readConsistency, "Read Consistency");
    }

    public String getId() {
        return signalId;
    }

    public LookupContext<?> getContext() {
        return context;
    }

    public ReadConsistency getReadConsistency() {
        return readConsistency;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final LookupEnforcer that = (LookupEnforcer) o;
        return Objects.equals(signalId, that.signalId) &&
                Objects.equals(context, that.context) &&
                readConsistency == that.readConsistency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(signalId, context, readConsistency);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "id=" + signalId +
                ", context=" + context +
                ", readConsistency=" + readConsistency +
                "]";
    }

}
