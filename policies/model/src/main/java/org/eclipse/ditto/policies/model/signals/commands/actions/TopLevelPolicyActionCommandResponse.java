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
package org.eclipse.ditto.policies.model.signals.commands.actions;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link TopLevelPolicyActionCommand} command.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableCommandResponse(type = TopLevelPolicyActionCommandResponse.TYPE)
public final class TopLevelPolicyActionCommandResponse
        extends AbstractCommandResponse<TopLevelPolicyActionCommandResponse>
        implements PolicyActionCommandResponse<TopLevelPolicyActionCommandResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + TopLevelPolicyActionCommand.NAME;

    /**
     * Status code of this response.
     */
    public static final HttpStatus STATUS = HttpStatus.NO_CONTENT;

    private final PolicyId policyId;

    private TopLevelPolicyActionCommandResponse(final PolicyId policyId, final DittoHeaders dittoHeaders) {
        super(TYPE, STATUS, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
    }

    /**
     * Creates a response to an {@code TopLevelActionCommand} command.
     *
     * @param policyId the policy ID.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static TopLevelPolicyActionCommandResponse of(final PolicyId policyId, final DittoHeaders dittoHeaders) {
        return new TopLevelPolicyActionCommandResponse(policyId, dittoHeaders);
    }

    /**
     * Creates a response to a {@code TopLevelActionCommand} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    @SuppressWarnings("unused") // called by reflection
    public static TopLevelPolicyActionCommandResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        final PolicyId policyId =
                PolicyId.of(jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID));
        return new TopLevelPolicyActionCommandResponse(policyId, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
    }

    @Override
    public TopLevelPolicyActionCommandResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new TopLevelPolicyActionCommandResponse(policyId, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof TopLevelPolicyActionCommandResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TopLevelPolicyActionCommandResponse that = (TopLevelPolicyActionCommandResponse) o;
        return Objects.equals(policyId, that.policyId) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                " [" + super.toString() +
                ", policyId=" + policyId +
                "]";
    }

}
