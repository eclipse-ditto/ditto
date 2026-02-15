/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Response to a {@link MigrateConnectionEncryption} command containing migration results.
 */
@Immutable
@JsonParsableCommandResponse(type = MigrateConnectionEncryptionResponse.TYPE)
public final class MigrateConnectionEncryptionResponse
        extends AbstractCommandResponse<MigrateConnectionEncryptionResponse> {

    static final String TYPE_PREFIX = "connectivity.responses:";

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + MigrateConnectionEncryption.NAME;

    static final JsonFieldDefinition<String> JSON_PHASE =
            JsonFactory.newStringFieldDefinition("phase", JsonSchemaVersion.V_2);
    static final JsonFieldDefinition<Boolean> JSON_DRY_RUN =
            JsonFactory.newBooleanFieldDefinition("dryRun", JsonSchemaVersion.V_2);
    static final JsonFieldDefinition<Boolean> JSON_RESUMED =
            JsonFactory.newBooleanFieldDefinition("resumed", JsonSchemaVersion.V_2);
    static final JsonFieldDefinition<String> JSON_STARTED_AT =
            JsonFactory.newStringFieldDefinition("startedAt", JsonSchemaVersion.V_2);
    static final JsonFieldDefinition<JsonObject> JSON_SNAPSHOTS =
            JsonFactory.newJsonObjectFieldDefinition("snapshots", JsonSchemaVersion.V_2);
    static final JsonFieldDefinition<JsonObject> JSON_JOURNAL_EVENTS =
            JsonFactory.newJsonObjectFieldDefinition("journalEvents", JsonSchemaVersion.V_2);

    private static final String RESOURCE_TYPE = "connectivity";
    private static final Set<HttpStatus> ALLOWED_HTTP_STATUSES =
            Set.of(HttpStatus.OK, HttpStatus.ACCEPTED);

    private static final CommandResponseJsonDeserializer<MigrateConnectionEncryptionResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new MigrateConnectionEncryptionResponse(
                                jsonObject.getValueOrThrow(JSON_PHASE),
                                jsonObject.getValueOrThrow(JSON_DRY_RUN),
                                jsonObject.getValue(JSON_RESUMED).orElse(false),
                                jsonObject.getValue(JSON_STARTED_AT).orElse(null),
                                jsonObject.getValue(JSON_SNAPSHOTS).orElse(null),
                                jsonObject.getValue(JSON_JOURNAL_EVENTS).orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders());
                    });

    private final String phase;
    private final boolean dryRun;
    private final boolean resumed;
    @Nullable
    private final String startedAt;
    @Nullable
    private final JsonObject snapshots;
    @Nullable
    private final JsonObject journalEvents;

    private MigrateConnectionEncryptionResponse(final String phase,
            final boolean dryRun,
            final boolean resumed,
            @Nullable final String startedAt,
            @Nullable final JsonObject snapshots,
            @Nullable final JsonObject journalEvents,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {
        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        ALLOWED_HTTP_STATUSES,
                        MigrateConnectionEncryptionResponse.class),
                dittoHeaders);
        this.phase = phase;
        this.dryRun = dryRun;
        this.resumed = resumed;
        this.startedAt = startedAt;
        this.snapshots = snapshots;
        this.journalEvents = journalEvents;
    }

    /**
     * Creates a response for an accepted (async) migration - returns 202 Accepted immediately.
     * The migration runs in the background; use the status command to query progress.
     *
     * @param resumed whether migration was resumed from previous state.
     * @param startedAt when migration started (ISO-8601 timestamp), may be {@code null}.
     * @param dryRun whether this is a dry-run (no changes will be made).
     * @param dittoHeaders the headers.
     * @return the response with HTTP 202 Accepted.
     */
    public static MigrateConnectionEncryptionResponse accepted(
            final boolean resumed,
            @Nullable final String startedAt,
            final boolean dryRun,
            final DittoHeaders dittoHeaders) {
        return new MigrateConnectionEncryptionResponse("started", dryRun, resumed, startedAt,
                null, null, HttpStatus.ACCEPTED, dittoHeaders);
    }

    /**
     * Creates a response indicating that a previous migration already completed and there is nothing to resume.
     * Returns 200 OK with phase "already_completed".
     *
     * @param timestamp the current timestamp (ISO-8601).
     * @param dittoHeaders the headers.
     * @return the response with HTTP 200 OK.
     */
    public static MigrateConnectionEncryptionResponse alreadyCompleted(
            @Nullable final String timestamp,
            final DittoHeaders dittoHeaders) {
        return new MigrateConnectionEncryptionResponse("already_completed", false, true, timestamp,
                null, null, HttpStatus.OK, dittoHeaders);
    }

    /**
     * Creates a response for a completed dry-run - returns 200 OK with counts.
     *
     * @param phase the final migration phase (typically "completed").
     * @param resumed whether migration was resumed from previous state.
     * @param startedAt when migration started (ISO-8601 timestamp), may be {@code null}.
     * @param snapshotsProcessed number of snapshots that would be migrated.
     * @param snapshotsSkipped number of snapshots skipped (already migrated).
     * @param snapshotsFailed number of snapshots that failed.
     * @param journalProcessed number of journal events that would be migrated.
     * @param journalSkipped number of journal events skipped (already migrated).
     * @param journalFailed number of journal events that failed.
     * @param dittoHeaders the headers.
     * @return the response with HTTP 200 OK and counts.
     */
    public static MigrateConnectionEncryptionResponse dryRunCompleted(final String phase,
            final boolean resumed,
            @Nullable final String startedAt,
            final long snapshotsProcessed, final long snapshotsSkipped, final long snapshotsFailed,
            final long journalProcessed, final long journalSkipped, final long journalFailed,
            final DittoHeaders dittoHeaders) {

        final JsonObject snapshotsObj = JsonFactory.newObjectBuilder()
                .set("processed", snapshotsProcessed)
                .set("skipped", snapshotsSkipped)
                .set("failed", snapshotsFailed)
                .build();
        final JsonObject journalObj = JsonFactory.newObjectBuilder()
                .set("processed", journalProcessed)
                .set("skipped", journalSkipped)
                .set("failed", journalFailed)
                .build();

        return new MigrateConnectionEncryptionResponse(phase, true, resumed, startedAt,
                snapshotsObj, journalObj, HttpStatus.OK, dittoHeaders);
    }

    /**
     * Creates a new {@code MigrateConnectionEncryptionResponse} from a JSON object.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers.
     * @return the response.
     */
    public static MigrateConnectionEncryptionResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    /**
     * Returns the current migration phase.
     *
     * @return the phase string.
     */
    public String getPhase() {
        return phase;
    }

    /**
     * Returns whether this was a dry-run.
     *
     * @return {@code true} if dry-run.
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * Returns whether migration was resumed.
     *
     * @return {@code true} if migration was resumed from previous state.
     */
    public boolean isResumed() {
        return resumed;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_PHASE, phase, predicate);
        jsonObjectBuilder.set(JSON_DRY_RUN, dryRun, predicate);
        jsonObjectBuilder.set(JSON_RESUMED, resumed, predicate);
        if (startedAt != null) {
            jsonObjectBuilder.set(JSON_STARTED_AT, startedAt, predicate);
        }
        if (snapshots != null) {
            jsonObjectBuilder.set(JSON_SNAPSHOTS, snapshots, predicate);
        }
        if (journalEvents != null) {
            jsonObjectBuilder.set(JSON_JOURNAL_EVENTS, journalEvents, predicate);
        }
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
    public MigrateConnectionEncryptionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new MigrateConnectionEncryptionResponse(phase, dryRun, resumed, startedAt,
                snapshots, journalEvents, getHttpStatus(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof MigrateConnectionEncryptionResponse;
    }

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
        final MigrateConnectionEncryptionResponse that = (MigrateConnectionEncryptionResponse) o;
        return dryRun == that.dryRun &&
                resumed == that.resumed &&
                Objects.equals(phase, that.phase) &&
                Objects.equals(startedAt, that.startedAt) &&
                Objects.equals(snapshots, that.snapshots) &&
                Objects.equals(journalEvents, that.journalEvents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), phase, dryRun, resumed, startedAt, snapshots, journalEvents);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", phase=" + phase +
                ", dryRun=" + dryRun +
                ", resumed=" + resumed +
                ", startedAt=" + startedAt +
                ", snapshots=" + snapshots +
                ", journalEvents=" + journalEvents +
                "]";
    }

}
