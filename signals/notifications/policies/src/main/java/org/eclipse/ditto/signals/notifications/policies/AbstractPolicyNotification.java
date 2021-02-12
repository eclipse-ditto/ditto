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
package org.eclipse.ditto.signals.notifications.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.signals.notifications.base.AbstractNotification;

/**
 * Abstract superclass of policy notifications.
 *
 * @param <T> type of a concrete subclass.
 */
public abstract class AbstractPolicyNotification<T extends AbstractPolicyNotification<T>>
        extends AbstractNotification<T>
        implements PolicyNotification<T> {

    private final PolicyId policyId;

    /**
     * Create a policy notification object.
     *
     * @param policyId the policy ID.
     * @param dittoHeaders the Ditto headers.
     */
    protected AbstractPolicyNotification(final PolicyId policyId, final DittoHeaders dittoHeaders) {
        super(dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
    }

    /**
     * Append policy-notification-specific payload to the passed {@code jsonObjectBuilder}.
     *
     * @param jsonObjectBuilder the JsonObjectBuilder to add the payload to.
     * @param predicate the predicate to evaluate when adding the payload.
     */
    protected abstract void appendPolicyNotificationPayload(final JsonObjectBuilder jsonObjectBuilder,
            final Predicate<JsonField> predicate);

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
        jsonObjectBuilder.set(JSON_POLICY_ID, policyId.toString());
        appendPolicyNotificationPayload(jsonObjectBuilder, predicate);
    }

    @Override
    public String toString() {
        return super.toString() + ", policyId=" + policyId;
    }

    @Override
    public boolean equals(final Object other) {
        if (super.equals(other)) {
            return Objects.equals(((AbstractPolicyNotification<?>) other).policyId, policyId);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(policyId, super.hashCode());
    }
}
