/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.signals.events;

import java.time.Instant;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.events.AbstractEventsourcedEvent;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Abstract base class of a {@link PolicyEvent}.
 *
 * @param <T> the type of the implementing class.
 */
@Immutable
public abstract class AbstractPolicyEvent<T extends AbstractPolicyEvent<T>> extends AbstractEventsourcedEvent<T>
        implements PolicyEvent<T> {

    private final PolicyId policyId;

    /**
     * Constructs a new {@code AbstractPolicyEvent} object.
     *
     * @param type the type of this event.
     * @param policyId the TYPE of the Policy with which this event is associated.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of the event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata which was applied together with the event, relative to the event's
     * {@link #getResourcePath()}.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    protected AbstractPolicyEvent(final String type,
            final PolicyId policyId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(type, policyId, timestamp, dittoHeaders, metadata, revision, PolicyEvent.JsonFields.POLICY_ID);
        this.policyId = policyId;
    }

    @Override
    public PolicyId getEntityId() {
        return getPolicyEntityId();
    }

    @Override
    public PolicyId getPolicyEntityId() {
        return policyId;
    }

    @SuppressWarnings({"squid:S1067"})
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final AbstractPolicyEvent<?> that = (AbstractPolicyEvent<?>) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractPolicyEvent;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(policyId);
        return result;
    }

    @Override
    public String toString() {
        return super.toString() + ", policyId=" + policyId;
    }

}
