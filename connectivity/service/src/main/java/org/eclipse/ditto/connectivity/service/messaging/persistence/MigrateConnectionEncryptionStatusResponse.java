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

import java.util.Collections;
import java.util.Objects;
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
 * Response to a {@link MigrateConnectionEncryptionStatus} command containing detailed migration progress.
 */
@Immutable
@JsonParsableCommandResponse(type = MigrateConnectionEncryptionStatusResponse.TYPE)
public final class MigrateConnectionEncryptionStatusResponse
        extends AbstractCommandResponse<MigrateConnectionEncryptionStatusResponse> {

    static final String TYPE_PREFIX = "connectivity.responses:";

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + MigrateConnectionEncryptionStatus.NAME;

    static final JsonFieldDefinition<String> JSON_PHASE =
            JsonFactory.newStringFieldDefinition("phase", JsonSchemaVersion.V_2);
    static final JsonFieldDefinition<JsonObject> JSON_SNAPSHOTS =
            JsonFactory.newJsonObjectFieldDefinition("snapshots", JsonSchemaVersion.V_2);
    static final JsonFieldDefinition<JsonObject> JSON_JOURNAL_EVENTS =
            JsonFactory.newJsonObjectFieldDefinition("journalEvents", JsonSchemaVersion.V_2);
    static final JsonFieldDefinition<JsonObject> JSON_PROGRESS =
            JsonFactory.newJsonObjectFieldDefinition("progress", JsonSchemaVersion.V_2);
    static final JsonFieldDefinition<JsonObject> JSON_TIMING =
            JsonFactory.newJsonObjectFieldDefinition("timing", JsonSchemaVersion.V_2);
    static final JsonFieldDefinition<Boolean> JSON_DRY_RUN =
            JsonFactory.newBooleanFieldDefinition("dryRun", JsonSchemaVersion.V_2);
    static final JsonFieldDefinition<Boolean> JSON_MIGRATION_ACTIVE =
            JsonFactory.newBooleanFieldDefinition("migrationActive", JsonSchemaVersion.V_2);

    private static final String RESOURCE_TYPE = "connectivity";
    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<MigrateConnectionEncryptionStatusResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new MigrateConnectionEncryptionStatusResponse(
                                jsonObject.getValueOrThrow(JSON_PHASE),
                                jsonObject.getValueOrThrow(JSON_SNAPSHOTS),
                                jsonObject.getValueOrThrow(JSON_JOURNAL_EVENTS),
                                jsonObject.getValue(JSON_PROGRESS).orElse(null),
                                jsonObject.getValue(JSON_TIMING).orElse(null),
                                jsonObject.getValue(JSON_DRY_RUN).orElse(false),
                                jsonObject.getValue(JSON_MIGRATION_ACTIVE).orElse(false),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders());
                    });

    private final String phase;
    private final JsonObject snapshots;
    private final JsonObject journalEvents;
    @Nullable
    private final JsonObject progress;
    @Nullable
    private final JsonObject timing;
    private final boolean dryRun;
    private final boolean migrationActive;

    private MigrateConnectionEncryptionStatusResponse(final String phase,
            final JsonObject snapshots,
            final JsonObject journalEvents,
            @Nullable final JsonObject progress,
            @Nullable final JsonObject timing,
            final boolean dryRun,
            final boolean migrationActive,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {
        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        MigrateConnectionEncryptionStatusResponse.class),
                dittoHeaders);
        this.phase = phase;
        this.snapshots = snapshots;
        this.journalEvents = journalEvents;
        this.progress = progress;
        this.timing = timing;
        this.dryRun = dryRun;
        this.migrationActive = migrationActive;
    }

    /**
     * Creates a new {@code MigrateConnectionEncryptionStatusResponse}.
     *
     * @param phase the current migration phase.
     * @param snapshotsProcessed number of snapshots processed.
     * @param snapshotsSkipped number of snapshots skipped.
     * @param snapshotsFailed number of snapshots that failed.
     * @param journalProcessed number of journal documents processed.
     * @param journalSkipped number of journal documents skipped.
     * @param journalFailed number of journal documents that failed.
     * @param lastProcessedSnapshotId last processed snapshot document ID, may be {@code null}.
     * @param lastProcessedSnapshotPid last processed snapshot persistence ID (connection ID), may be {@code null}.
     * @param lastProcessedJournalId last processed journal document ID, may be {@code null}.
     * @param lastProcessedJournalPid last processed journal persistence ID (connection ID), may be {@code null}.
     * @param startedAt when migration started, may be {@code null}.
     * @param updatedAt when migration was last updated, may be {@code null}.
     * @param dryRun whether the migration was/is a dry-run.
     * @param migrationActive whether migration is currently active.
     * @param dittoHeaders the headers.
     * @return the response.
     */
    public static MigrateConnectionEncryptionStatusResponse of(final String phase,
            final long snapshotsProcessed, final long snapshotsSkipped, final long snapshotsFailed,
            final long journalProcessed, final long journalSkipped, final long journalFailed,
            @Nullable final String lastProcessedSnapshotId, @Nullable final String lastProcessedSnapshotPid,
            @Nullable final String lastProcessedJournalId, @Nullable final String lastProcessedJournalPid,
            @Nullable final String startedAt, @Nullable final String updatedAt,
            final boolean dryRun,
            final boolean migrationActive,
            final DittoHeaders dittoHeaders) {

        final JsonObject snapshots = JsonFactory.newObjectBuilder()
                .set("processed", snapshotsProcessed)
                .set("skipped", snapshotsSkipped)
                .set("failed", snapshotsFailed)
                .build();
        final JsonObject journal = JsonFactory.newObjectBuilder()
                .set("processed", journalProcessed)
                .set("skipped", journalSkipped)
                .set("failed", journalFailed)
                .build();

        final JsonObjectBuilder progressBuilder = JsonFactory.newObjectBuilder();
        if (lastProcessedSnapshotId != null) {
            progressBuilder.set("lastProcessedSnapshotId", lastProcessedSnapshotId);
        }
        if (lastProcessedSnapshotPid != null) {
            progressBuilder.set("lastProcessedSnapshotPid", lastProcessedSnapshotPid);
        }
        if (lastProcessedJournalId != null) {
            progressBuilder.set("lastProcessedJournalId", lastProcessedJournalId);
        }
        if (lastProcessedJournalPid != null) {
            progressBuilder.set("lastProcessedJournalPid", lastProcessedJournalPid);
        }
        final JsonObject progress = progressBuilder.build();

        final JsonObjectBuilder timingBuilder = JsonFactory.newObjectBuilder();
        if (startedAt != null) {
            timingBuilder.set("startedAt", startedAt);
        }
        if (updatedAt != null) {
            timingBuilder.set("updatedAt", updatedAt);
        }
        final JsonObject timing = timingBuilder.build();

        return new MigrateConnectionEncryptionStatusResponse(phase, snapshots, journal,
                progress.isEmpty() ? null : progress,
                timing.isEmpty() ? null : timing,
                dryRun,
                migrationActive,
                HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a new {@code MigrateConnectionEncryptionStatusResponse} from a JSON object.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers.
     * @return the response.
     */
    public static MigrateConnectionEncryptionStatusResponse fromJson(final JsonObject jsonObject,
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
     * Returns whether migration is currently active.
     *
     * @return {@code true} if migration is active.
     */
    public boolean isMigrationActive() {
        return migrationActive;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_PHASE, phase, predicate);
        jsonObjectBuilder.set(JSON_SNAPSHOTS, snapshots, predicate);
        jsonObjectBuilder.set(JSON_JOURNAL_EVENTS, journalEvents, predicate);
        if (progress != null) {
            jsonObjectBuilder.set(JSON_PROGRESS, progress, predicate);
        }
        if (timing != null) {
            jsonObjectBuilder.set(JSON_TIMING, timing, predicate);
        }
        jsonObjectBuilder.set(JSON_DRY_RUN, dryRun, predicate);
        jsonObjectBuilder.set(JSON_MIGRATION_ACTIVE, migrationActive, predicate);
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
    public MigrateConnectionEncryptionStatusResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new MigrateConnectionEncryptionStatusResponse(phase, snapshots, journalEvents,
                progress, timing, dryRun, migrationActive, HTTP_STATUS, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof MigrateConnectionEncryptionStatusResponse;
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
        final MigrateConnectionEncryptionStatusResponse that = (MigrateConnectionEncryptionStatusResponse) o;
        return dryRun == that.dryRun &&
                migrationActive == that.migrationActive &&
                Objects.equals(phase, that.phase) &&
                Objects.equals(snapshots, that.snapshots) &&
                Objects.equals(journalEvents, that.journalEvents) &&
                Objects.equals(progress, that.progress) &&
                Objects.equals(timing, that.timing);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), phase, snapshots, journalEvents, progress, timing, dryRun, migrationActive);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", phase=" + phase +
                ", snapshots=" + snapshots +
                ", journalEvents=" + journalEvents +
                ", progress=" + progress +
                ", timing=" + timing +
                ", dryRun=" + dryRun +
                ", migrationActive=" + migrationActive +
                "]";
    }

}
