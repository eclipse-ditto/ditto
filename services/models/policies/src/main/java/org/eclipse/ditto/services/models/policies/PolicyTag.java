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
package org.eclipse.ditto.services.models.policies;

import static org.eclipse.ditto.json.JsonFactory.newFieldDefinition;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectReader;
import org.eclipse.ditto.json.JsonReader;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Represents the ID of a Policy with a revision of the Policy combined as its own type.
 */
@Immutable
public final class PolicyTag implements Jsonifiable {

    /**
     * Topic at which {@code PolicyTag} objects are published.
     */
    public static final String TOPIC = "policies.tags";

    private final String policyId;
    private final long revision;

    private PolicyTag(final String policyId, final long revision) {
        this.policyId = policyId;
        this.revision = revision;
    }

    /**
     * Returns a new {@code PolicyTag}.
     *
     * @param policyId the ID of the modified Policy.
     * @param revision the revision of the modified Policy.
     * @return a new PolicyTag.
     */
    public static PolicyTag of(final String policyId, final long revision) {
        checkNotNull(policyId, "Policy ID");
        checkNotNull(revision, "Revision");

        return new PolicyTag(policyId, revision);
    }

    /**
     * Creates a new {@code PolicyTag} from a JSON string.
     *
     * @param jsonString the JSON string of which a new PolicyTag is to be created.
     * @return the PolicyTag which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} does not contain a JSON
     * object or if it is not valid JSON.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonString} was not in the
     * expected 'PolicyTag' format.
     */
    public static PolicyTag fromJson(final String jsonString) {
        return fromJson(JsonFactory.newObject(jsonString));
    }

    /**
     * Creates a new {@code PolicyTag} from a JSON string.
     *
     * @param jsonObject the JSON object of which a new PolicyTag is to be created.
     * @return the PolicyTag which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} is not valid JSON.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected 'PolicyTag' format.
     */
    public static PolicyTag fromJson(final JsonObject jsonObject) {
        final JsonObjectReader jsonObjectReader = JsonReader.from(jsonObject);
        final String extractedPolicyId = jsonObjectReader.get(JsonFields.ID);
        final long extractedRevision = jsonObjectReader.get(JsonFields.REVISION);

        return of(extractedPolicyId, extractedRevision);
    }

    /**
     * Returns the ID of the modified Policy.
     *
     * @return the ID of the modified Policy.
     */
    public String getPolicyId() {
        return policyId;
    }

    /**
     * Returns the revision of the modified Policy.
     *
     * @return the revision of the modified Policy.
     */
    public long getRevision() {
        return revision;
    }

    @Override
    public JsonValue toJson() {
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.ID, policyId)
                .set(JsonFields.REVISION, revision)
                .build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(policyId, revision);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "pmd:SimplifyConditional"})
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final PolicyTag that = (PolicyTag) obj;
        return Objects.equals(policyId, that.policyId) && Objects.equals(revision, that.revision);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "policyId=" + policyId + ", revision=" + revision + "]";
    }

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a PolicyTag.
     *
     */
    public static final class JsonFields {

        /**
         * JSON field containing the PolicyTag's ID as String.
         */
        public static final JsonFieldDefinition ID = newFieldDefinition("policyId", String.class);

        /**
         * JSON field containing the PolicyTag's revision as long value.
         */
        public static final JsonFieldDefinition REVISION = newFieldDefinition("revision", long.class);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
