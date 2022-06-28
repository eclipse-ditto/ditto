/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.api;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.internal.models.streaming.IdentifiableStreamingMessage;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Represents the ID and revision of a Policy combined with an ID of another entity referencing this policy.
 */
@Immutable
public final class PolicyReferenceTag implements IdentifiableStreamingMessage, Jsonifiable<JsonObject> {

    /**
     * Use a separator which cannot overlap with an entityId
     **/
    private static final String ENTITY_ID_FROM_POLICY_TAG_SEPARATOR = "/";

    private final ThingId thingId;
    private final PolicyTag policyTag;

    private PolicyReferenceTag(final ThingId thingId, final PolicyTag policyTag) {
        this.thingId = requireNonNull(thingId);
        this.policyTag = requireNonNull(policyTag);
    }

    /**
     * Returns a new {@link PolicyReferenceTag}.
     *
     * @param entityId the ID of the entity referencing the policy.
     * @param policyTag the {@link PolicyTag}.
     * @return a new {@link PolicyReferenceTag}.
     */
    public static PolicyReferenceTag of(final ThingId entityId, final PolicyTag policyTag) {
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
        final ThingId thingId = ThingId.of(extractedEntityId);
        final String extractedPolicyId = jsonObject.getValueOrThrow(JsonFields.POLICY_ID);
        final PolicyId policyId = PolicyId.of(extractedPolicyId);
        final long extractedPolicyRev = jsonObject.getValueOrThrow(JsonFields.POLICY_REV);

        final PolicyTag extractedPolicyTag = PolicyTag.of(policyId, extractedPolicyRev);

        return new PolicyReferenceTag(thingId, extractedPolicyTag);
    }

    /**
     * Returns the ID of the thing referencing the policy.
     *
     * @return the ID
     */
    public ThingId getThingId() {
        return thingId;
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
                .set(JsonFields.ENTITY_ID, String.valueOf(thingId))
                .set(JsonFields.POLICY_ID, String.valueOf(policyTag.getEntityId()))
                .set(JsonFields.POLICY_REV, policyTag.getRevision())
                .build();
    }

    @Override
    public String asIdentifierString() {
        return thingId + ENTITY_ID_FROM_POLICY_TAG_SEPARATOR + policyTag.getRevision();
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
        return Objects.equals(thingId, that.thingId) &&
                Objects.equals(policyTag, that.policyTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingId, policyTag);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingId='" + thingId + '\'' +
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
