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
package org.eclipse.ditto.services.models.streaming;

import static org.eclipse.ditto.model.base.json.FieldType.REGULAR;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_1;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Command which starts a stream of persistence IDs with their latest snapshot revisions.
 */
@Immutable
@AllValuesAreNonnullByDefault
@JsonParsableCommand(typePrefix = SudoStreamSnapshotRevisions.TYPE_PREFIX, name = SudoStreamSnapshotRevisions.NAME)
public final class SudoStreamSnapshotRevisions extends AbstractCommand<SudoStreamSnapshotRevisions>
        implements StartStreamRequest {

    static final String NAME = "SudoStreamSnapshotRevisions";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<Integer> JSON_BURST =
            JsonFactory.newIntFieldDefinition("payload/burst", REGULAR, V_1, V_2);

    static final JsonFieldDefinition<Long> JSON_TIMEOUT_MILLIS =
            JsonFactory.newLongFieldDefinition("payload/timeoutMillis", REGULAR, V_1, V_2);

    private final int burst;

    private final long timeoutMillis;

    private SudoStreamSnapshotRevisions(final Integer burst, final Long timeoutMillis,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);

        this.burst = burst;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Creates a new {@code SudoStreamSnapshotRevisions} command.
     *
     * @param burst the amount of elements to be collected per message
     * @param timeoutMillis maximum time to wait for acknowledgement of each stream element.
     * @param dittoHeaders the command headers of the request.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoStreamSnapshotRevisions of(final Integer burst, final Long timeoutMillis,
            final DittoHeaders dittoHeaders) {

        return new SudoStreamSnapshotRevisions(burst, timeoutMillis, dittoHeaders);
    }

    /**
     * Creates a new {@code SudoStreamSnapshotRevisions} from a JSON object.
     *
     * @param jsonObject the JSON representation of the command.
     * @param dittoHeaders the optional command headers of the request.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static SudoStreamSnapshotRevisions fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        final int burst = jsonObject.getValueOrThrow(JSON_BURST);
        final long timeoutMillis = jsonObject.getValueOrThrow(JSON_TIMEOUT_MILLIS);
        return SudoStreamSnapshotRevisions.of(burst, timeoutMillis, dittoHeaders);
    }

    @Override
    public int getBurst() {
        return burst;
    }

    @Override
    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    @Override
    public <T> T accept(final StartStreamRequestVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_BURST, burst, predicate);
        jsonObjectBuilder.set(JSON_TIMEOUT_MILLIS, timeoutMillis, predicate);
    }

    @Override
    public String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public SudoStreamSnapshotRevisions setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(burst, timeoutMillis, dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), burst, timeoutMillis);
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (obj instanceof SudoStreamSnapshotRevisions) {
            final SudoStreamSnapshotRevisions that = (SudoStreamSnapshotRevisions) obj;
            return burst == that.burst && timeoutMillis == that.timeoutMillis && super.equals(that);
        } else {
            return false;
        }
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoStreamSnapshotRevisions;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString()
                + ", burst=" + burst
                + ", timeoutMillis=" + timeoutMillis
                + "]";
    }

    @Override
    public String getId() {
        return "";
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return TYPE;
    }
}
