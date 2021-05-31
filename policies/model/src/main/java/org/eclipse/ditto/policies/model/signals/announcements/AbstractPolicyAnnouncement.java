/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.signals.announcements;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.base.model.signals.announcements.AbstractAnnouncement;

/**
 * Abstract superclass of policy announcements.
 *
 * @param <T> type of a concrete subclass.
 * @since 2.0.0
 */
public abstract class AbstractPolicyAnnouncement<T extends AbstractPolicyAnnouncement<T>>
        extends AbstractAnnouncement<T> implements PolicyAnnouncement<T> {

    private final PolicyId policyId;

    /**
     * Create a policy announcement object.
     *
     * @param policyId the policy ID.
     * @param dittoHeaders the Ditto headers.
     */
    protected AbstractPolicyAnnouncement(final PolicyId policyId, final DittoHeaders dittoHeaders) {
        super(dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
    }

    /**
     * Append {@code PolicyAnnouncement}-specific payload to the passed {@code jsonObjectBuilder}.
     *
     * @param jsonObjectBuilder the JsonObjectBuilder to add the payload to.
     * @param predicate the predicate to evaluate when adding the payload.
     */
    protected abstract void appendPolicyAnnouncementPayload(JsonObjectBuilder jsonObjectBuilder,
            Predicate<JsonField> predicate);

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(PolicyAnnouncement.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
        appendPolicyAnnouncementPayload(jsonObjectBuilder, predicate);
    }

    @Override
    public String toString() {
        return super.toString() + ", policyId=" + policyId;
    }

    @Override
    public boolean equals(final Object other) {
        if (super.equals(other)) {
            return Objects.equals(((AbstractPolicyAnnouncement<?>) other).policyId, policyId);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(policyId, super.hashCode());
    }
}
