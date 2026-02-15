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
 * Response to a {@link MigrateConnectionEncryptionAbort} command containing final migration state.
 */
@Immutable
@JsonParsableCommandResponse(type = MigrateConnectionEncryptionAbortResponse.TYPE)
public final class MigrateConnectionEncryptionAbortResponse
        extends AbstractCommandResponse<MigrateConnectionEncryptionAbortResponse> {

    static final String TYPE_PREFIX = "connectivity.responses:";

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + MigrateConnectionEncryptionAbort.NAME;

    static final JsonFieldDefinition<String> JSON_PHASE =
            JsonFactory.newStringFieldDefinition("phase", JsonSchemaVersion.V_2);
    static final JsonFieldDefinition<JsonObject> JSON_SNAPSHOTS =
            JsonFactory.newJsonObjectFieldDefinition("snapshots", JsonSchemaVersion.V_2);
    static final JsonFieldDefinition<JsonObject> JSON_JOURNAL_EVENTS =
            JsonFactory.newJsonObjectFieldDefinition("journalEvents", JsonSchemaVersion.V_2);
    static final JsonFieldDefinition<String> JSON_ABORTED_AT =
            JsonFactory.newStringFieldDefinition("abortedAt", JsonSchemaVersion.V_2);

    private static final String RESOURCE_TYPE = "connectivity";
    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<MigrateConnectionEncryptionAbortResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new MigrateConnectionEncryptionAbortResponse(
                                jsonObject.getValueOrThrow(JSON_PHASE),
                                jsonObject.getValueOrThrow(JSON_SNAPSHOTS),
                                jsonObject.getValueOrThrow(JSON_JOURNAL_EVENTS),
                                jsonObject.getValueOrThrow(JSON_ABORTED_AT),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders());
                    });

    private final String phase;
    private final JsonObject snapshots;
    private final JsonObject journalEvents;
    private final String abortedAt;

    private MigrateConnectionEncryptionAbortResponse(final String phase,
            final JsonObject snapshots,
            final JsonObject journalEvents,
            final String abortedAt,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {
        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        MigrateConnectionEncryptionAbortResponse.class),
                dittoHeaders);
        this.phase = phase;
        this.snapshots = snapshots;
        this.journalEvents = journalEvents;
        this.abortedAt = abortedAt;
    }

    /**
     * Creates a new {@code MigrateConnectionEncryptionAbortResponse}.
     *
     * @param phase the current migration phase at time of abort.
     * @param snapshotsProcessed number of snapshots processed.
     * @param snapshotsSkipped number of snapshots skipped.
     * @param snapshotsFailed number of snapshots that failed.
     * @param journalProcessed number of journal documents processed.
     * @param journalSkipped number of journal documents skipped.
     * @param journalFailed number of journal documents that failed.
     * @param abortedAt when migration was aborted (ISO-8601 timestamp).
     * @param dittoHeaders the headers.
     * @return the response.
     */
    public static MigrateConnectionEncryptionAbortResponse of(final String phase,
            final long snapshotsProcessed, final long snapshotsSkipped, final long snapshotsFailed,
            final long journalProcessed, final long journalSkipped, final long journalFailed,
            final String abortedAt,
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
        return new MigrateConnectionEncryptionAbortResponse(phase, snapshots, journal, abortedAt,
                HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a new {@code MigrateConnectionEncryptionAbortResponse} from a JSON object.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers.
     * @return the response.
     */
    public static MigrateConnectionEncryptionAbortResponse fromJson(final JsonObject jsonObject,
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
     * Returns when the migration was aborted.
     *
     * @return the ISO-8601 timestamp.
     */
    public String getAbortedAt() {
        return abortedAt;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_PHASE, phase, predicate);
        jsonObjectBuilder.set(JSON_SNAPSHOTS, snapshots, predicate);
        jsonObjectBuilder.set(JSON_JOURNAL_EVENTS, journalEvents, predicate);
        jsonObjectBuilder.set(JSON_ABORTED_AT, abortedAt, predicate);
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
    public MigrateConnectionEncryptionAbortResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new MigrateConnectionEncryptionAbortResponse(phase, snapshots, journalEvents, abortedAt,
                HTTP_STATUS, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof MigrateConnectionEncryptionAbortResponse;
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
        final MigrateConnectionEncryptionAbortResponse that = (MigrateConnectionEncryptionAbortResponse) o;
        return Objects.equals(phase, that.phase) &&
                Objects.equals(snapshots, that.snapshots) &&
                Objects.equals(journalEvents, that.journalEvents) &&
                Objects.equals(abortedAt, that.abortedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), phase, snapshots, journalEvents, abortedAt);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", phase=" + phase +
                ", snapshots=" + snapshots +
                ", journalEvents=" + journalEvents +
                ", abortedAt=" + abortedAt +
                "]";
    }

}
