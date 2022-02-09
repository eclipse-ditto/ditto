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
package org.eclipse.ditto.thingsearch.api.commands.sudo;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldMarker;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommandResponse;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Command response to acknowledge a search index update.
 * Currently, a Ditto-internal message, but could become public API at some point.
 */
@Immutable
@AllValuesAreNonnullByDefault
@JsonParsableCommandResponse(type = UpdateThingResponse.TYPE)
// When making this a ThingSearchCommand, beware that it is WithId but actually yes.
public final class UpdateThingResponse extends AbstractCommandResponse<UpdateThingResponse>
        implements SignalWithEntityId<UpdateThingResponse> {

    /**
     * Type of this command.
     */
    public static final String TYPE = ThingSearchCommandResponse.TYPE_PREFIX + UpdateThing.NAME;

    private final ThingId thingId;
    private final long thingRevision;
    @Nullable private final PolicyId policyId;
    @Nullable private final Long policyRevision;
    private final boolean success;

    private UpdateThingResponse(final ThingId thingId,
            final long thingRevision,
            final boolean success,
            @Nullable final PolicyId policyId,
            @Nullable final Long policyRevision,
            final DittoHeaders dittoHeaders) {

        super(TYPE, HttpStatus.OK, dittoHeaders);
        this.thingId = thingId;
        this.thingRevision = thingRevision;
        this.success = success;
        this.policyId = policyId;
        this.policyRevision = policyRevision;
    }

    /**
     * Create an UpdateThing command.
     *
     * @param thingId the ID of the thing whose search index should be updated.
     * @param thingRevision revision of the requested update.
     * @param policyId the policy ID.
     * @param policyRevision the policy revision.
     * @param success whether the update is acknowledged by the persistence as successful.
     * @param dittoHeaders Ditto headers of the command.
     * @return the command.
     */
    public static UpdateThingResponse of(final ThingId thingId,
            final long thingRevision,
            @Nullable final PolicyId policyId,
            @Nullable final Long policyRevision,
            final boolean success,
            final DittoHeaders dittoHeaders) {

        return new UpdateThingResponse(thingId, thingRevision, success, policyId, policyRevision, dittoHeaders);
    }

    /**
     * Creates a new {@code UpdateThing} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain a value for
     * "thingId".
     */
    public static UpdateThingResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final ThingId thingId = ThingId.of(jsonObject.getValueOrThrow(JsonFields.THING_ID));
        final long thingRevision = jsonObject.getValueOrThrow(JsonFields.THING_REVISION);
        final PolicyId policyId = jsonObject.getValue(JsonFields.POLICY_ID).map(PolicyId::of).orElse(null);
        final Long policyRevision = jsonObject.getValue(JsonFields.POLICY_REVISION).orElse(null);
        final boolean success = jsonObject.getValueOrThrow(JsonFields.SUCCESS);
        return of(thingId, thingRevision, policyId, policyRevision, success, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(JsonFields.THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.THING_REVISION, thingRevision, predicate);
        if (policyId != null) {
            jsonObjectBuilder.set(JsonFields.POLICY_ID, policyId.toString(), predicate);
        }
        if (policyRevision != null) {
            jsonObjectBuilder.set(JsonFields.POLICY_REVISION, policyRevision, predicate);
        }
        jsonObjectBuilder.set(JsonFields.SUCCESS, success, predicate);
    }

    @Override
    public UpdateThingResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new UpdateThingResponse(thingId, thingRevision, success, policyId, policyRevision, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return getThingId();
    }

    /**
     * Retrieve the thing ID.
     *
     * @return the thing ID.
     */
    public ThingId getThingId() {
        return thingId;
    }

    /**
     * Retrieve the revision requested for the update.
     *
     * @return the revision.
     */
    public long getThingRevision() {
        return thingRevision;
    }

    /**
     * Retrieve the policy ID.
     *
     * @return the policy ID.
     */
    public Optional<PolicyId> getPolicyId() {
        return Optional.ofNullable(policyId);
    }

    /**
     * Retrieve the policy revision.
     *
     * @return the policy revision.
     */
    public Optional<Long> getPolicyRevision() {
        return Optional.ofNullable(policyRevision);
    }

    /**
     * Return whether the update is acknowledged by the persistence as successful.
     *
     * @return whether this is a success.
     */
    public boolean isSuccess() {
        return success;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return ThingSearchCommand.RESOURCE_TYPE;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof UpdateThingResponse)) {
            return false;
        } else {
            final UpdateThingResponse that = (UpdateThingResponse) o;
            return Objects.equals(thingId, that.thingId) &&
                    thingRevision == that.thingRevision &&
                    Objects.equals(policyId, that.policyId) &&
                    Objects.equals(policyRevision, that.policyRevision) &&
                    success == that.success &&
                    super.equals(that);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, thingRevision, policyId, policyRevision, success);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() +
                ",thingId=" + thingId +
                ",thingRevision=" + thingRevision +
                ",policyId=" + policyId +
                ",policyRevision=" + policyRevision +
                ",success=" + success +
                "]";
    }

    private static final class JsonFields {

        private static final JsonFieldDefinition<String> THING_ID = Thing.JsonFields.ID;
        private static final JsonFieldMarker[] JSON_FIELD_MARKERS =
                THING_ID.getMarkers().toArray(JsonFieldMarker[]::new);
        private static final JsonFieldDefinition<Long> THING_REVISION =
                JsonFactory.newLongFieldDefinition("thingRevision", JSON_FIELD_MARKERS);
        private static final JsonFieldDefinition<String> POLICY_ID = Thing.JsonFields.POLICY_ID;
        private static final JsonFieldDefinition<Long> POLICY_REVISION =
                JsonFactory.newLongFieldDefinition("policyRevision", JSON_FIELD_MARKERS);
        private static final JsonFieldDefinition<Boolean> SUCCESS =
                JsonFactory.newBooleanFieldDefinition("success", JSON_FIELD_MARKERS);

    }

}
