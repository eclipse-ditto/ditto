/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.pubsub.ddata;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A generic implementation of IndelUpdate.
 */
@NotThreadSafe
public abstract class AbstractSubscriptionsUpdate<S, T extends AbstractSubscriptionsUpdate<S, T>> implements
        DDataUpdate<S> {

    private Set<S> inserts;

    /**
     * Create an IndelUpdate.
     *
     * @param inserts what to insert.
     */
    protected AbstractSubscriptionsUpdate(final Set<S> inserts) {
        this.inserts = inserts;
    }

    // TODO: javadoc

    public abstract T snapshot();

    @Override
    public Set<S> getInserts() {
        return inserts;
    }

    protected void reset() {
        inserts = new HashSet<>();
    }

    public T exportAndReset() {
        final T snapshot = snapshot();
        this.reset();
        return snapshot;
    }

    @Override
    public boolean equals(final Object other) {
        if (getClass().isInstance(other)) {
            final AbstractSubscriptionsUpdate<?, ?> that = getClass().cast(other);
            return Objects.equals(inserts, that.inserts);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(inserts);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "inserts=" + inserts +
                "]";
    }
}
