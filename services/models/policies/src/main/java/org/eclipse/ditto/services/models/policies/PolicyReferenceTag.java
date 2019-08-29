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
package org.eclipse.ditto.services.models.policies;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.models.streaming.IdentifiableStreamingMessage;

/**
 * Represents the ID and revision of a Policy combined with an ID of another entity referencing this policy.
 */
@Immutable
public final class PolicyReferenceTag implements IdentifiableStreamingMessage, Jsonifiable<JsonObject> {

    /**
     * Use a separator which cannot overlap with an entityId
     **/
    private static final String ENTITY_ID_FROM_POLICY_TAG_SEPARATOR = "/";

    private final EntityId entityId;
    private final PolicyTag policyTag;

    private PolicyReferenceTag(final EntityId entityId, final PolicyTag policyTag) {
        this.entityId = requireNonNull(entityId);
        this.policyTag = requireNonNull(policyTag);
    }

    /**
     * Returns a new {@link PolicyReferenceTag}.
     *
     * @param entityId the ID of the entity referencing the policy.
     * @param policyTag the {@link PolicyTag}.
     * @return a new {@link PolicyReferenceTag}.
     */
    public static PolicyReferenceTag of(final EntityId entityId, final PolicyTag policyTag) {
        return new PolicyReferenceTag(entityId, policyTag);
    }

    /**
     * Creates a new {@link PolicyReferenceTag} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new {@link PolicyReferenceTag} is to be created.
     * @return the {@link PolicyReferenceTag} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} is not valid JSON.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected format.
     */
    public static PolicyReferenceTag fromJson(final JsonObject jsonObject) {
        final String extractedEntityId = jsonObject.getValueOrThrow(JsonFields.ENTITY_ID);
        final EntityId entityId = DefaultEntityId.of(extractedEntityId);
        final String extractedPolicyId = jsonObject.getValueOrThrow(JsonFields.POLICY_ID);
        final PolicyId policyId = PolicyId.of(extractedPolicyId);
        final long extractedPolicyRev = jsonObject.getValueOrThrow(JsonFields.POLICY_REV);

        final PolicyTag extractedPolicyTag = PolicyTag.of(policyId, extractedPolicyRev);

        return new PolicyReferenceTag(entityId, extractedPolicyTag);
    }

    /**
     * Returns the ID of the entity referencing the policy.
     *
     * @return the ID
     */
    public EntityId getEntityId() {
        return entityId;
    }

    /**
     * Returns the {@link PolicyTag}.
     *
     * @return the {@link PolicyTag}.
     */
    public PolicyTag getPolicyTag() {
        return policyTag;
    }

    @Override
    public JsonObject toJson() {
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.ENTITY_ID, String.valueOf(entityId))
                .set(JsonFields.POLICY_ID, String.valueOf(policyTag.getEntityId()))
                .set(JsonFields.POLICY_REV, policyTag.getRevision())
                .build();
    }

    @Override
    public String asIdentifierString() {
        return entityId + ENTITY_ID_FROM_POLICY_TAG_SEPARATOR + policyTag.getRevision();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PolicyReferenceTag that = (PolicyReferenceTag) o;
        return Objects.equals(entityId, that.entityId) &&
                Objects.equals(policyTag, that.policyTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, policyTag);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "entityId='" + entityId + '\'' +
                ", policyTag=" + policyTag +
                ']';
    }

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a PolicyReferenceTag.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * JSON field containing the entityId.
         */
        public static final JsonFieldDefinition<String> ENTITY_ID = JsonFactory.newStringFieldDefinition("entityId");

        /**
         * JSON field containing the policy-id.
         */
        public static final JsonFieldDefinition<String> POLICY_ID = JsonFactory.newStringFieldDefinition("policyId");

        /**
         * JSON field containing the policy-revision.
         */
        public static final JsonFieldDefinition<Long> POLICY_REV = JsonFactory.newLongFieldDefinition("policyRev");


        private JsonFields() {
            throw new AssertionError();
        }

    }
}
