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
package org.eclipse.ditto.policies.api;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.models.streaming.AbstractEntityIdWithRevision;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Represents the ID and revision of a Policy.
 */
@Immutable
public final class PolicyTag extends AbstractEntityIdWithRevision<PolicyId> {

    /**
     * Defines a Publish/Subscribe topic on which PolicyTag message are published whenever the policy was modified.
     */
    public static final String PUB_SUB_TOPIC_MODIFIED = "policy-modified";

    /**
     * Defines a Publish/Subscribe topic on which PolicyTag message are published whenever the policy enforcer caches
     * of the policyId contained in the published PolicyTag should be invalidated.
     */
    public static final String PUB_SUB_TOPIC_INVALIDATE_ENFORCERS = "policy-invalidate-enforcers";

    private PolicyTag(final PolicyId policyId, final long revision) {
        super(policyId, revision);
    }

    /**
     * Returns a new {@link PolicyTag}.
     *
     * @param policyId the ID of the modified Policy.
     * @param revision the revision of the modified Policy.
     * @return a new {@link PolicyTag}.
     */
    public static PolicyTag of(final PolicyId policyId, final long revision) {
        return new PolicyTag(policyId, revision);
    }

    /**
     * Creates a new {@link PolicyTag} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new {@link PolicyTag} is to be created.
     * @return the {@link PolicyTag} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} is not valid JSON.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected format.
     */
    public static PolicyTag fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");
        final PolicyId policyId = PolicyId.of(jsonObject.getValueOrThrow(JsonFields.ENTITY_ID));
        final Long revision = jsonObject.getValueOrThrow(JsonFields.REVISION);

        return new PolicyTag(policyId, revision);
    }

}
