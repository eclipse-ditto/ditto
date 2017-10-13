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
package org.eclipse.ditto.signals.commands.things.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingIdValidator;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * Command which retrieves one {@link org.eclipse.ditto.model.things.Thing} based on the the passed in Thing ID.
 */
@Immutable
public final class RetrieveThing extends AbstractCommand<RetrieveThing> implements ThingQueryCommand<RetrieveThing> {

    /**
     * Name of the "Retrieve Thing" command.
     */
    public static final String NAME = "retrieveThing";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_SELECTED_FIELDS =
            JsonFactory.newStringFieldDefinition("selectedFields", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<Long> JSON_SNAPSHOT_REVISION =
            JsonFactory.newLongFieldDefinition("snapshotRevision", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private static final long NULL_SNAPSHOT_REVISION = -1L;

    private final String thingId;
    @Nullable private final JsonFieldSelector selectedFields;
    private final long snapshotRevision;

    private RetrieveThing(final Builder builder) {
        super(TYPE, builder.dittoHeaders);
        thingId = builder.thingId;
        selectedFields = builder.selectedFields;
        snapshotRevision = builder.snapshotRevision;
    }

    /**
     * Returns a Command for retrieving the Thing with the given ID.
     *
     * @param thingId the ID of a single Thing to be retrieved by this command.
     * @param dittoHeaders the headers of the command.
     * @return a Command for retrieving the Thing with the {@code thingId} as its ID which is readable from the passed
     * authorization context.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static RetrieveThing of(final CharSequence thingId, final DittoHeaders dittoHeaders) {
        return getBuilder(thingId, dittoHeaders).build();
    }

    /**
     * Returns a builder with a fluent API for an immutable {@code RetrieveThing} instance.
     *
     * @param thingId the ID of a single Thing to be retrieved by this command.
     * @param dittoHeaders the headers of the command.
     * @return the builder.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static Builder getBuilder(final CharSequence thingId, final DittoHeaders dittoHeaders) {
        ThingIdValidator.getInstance().accept(thingId, dittoHeaders);
        return new Builder(thingId.toString(), checkNotNull(dittoHeaders, "command headers"));
    }

    /**
     * Creates a new {@code RetrieveThing} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveThing fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveThing} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveThing fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RetrieveThing>(TYPE, jsonObject).deserialize(() -> {
            final String thingId = jsonObject.getValueOrThrow(ThingQueryCommand.JsonFields.JSON_THING_ID);
            final Builder builder = getBuilder(thingId, dittoHeaders);

            jsonObject.getValue(JSON_SELECTED_FIELDS)
                    .map(str -> JsonFactory.newFieldSelector(str, JsonFactory.newParseOptionsBuilder()
                            .withoutUrlDecoding()
                            .build()))
                    .ifPresent(builder::withSelectedFields);

            jsonObject.getValue(JSON_SNAPSHOT_REVISION).ifPresent(builder::withSnapshotRevision);

            return builder.build();
        });
    }

    @Override
    public Optional<JsonFieldSelector> getSelectedFields() {
        return Optional.ofNullable(selectedFields);
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingQueryCommand.JsonFields.JSON_THING_ID, thingId, predicate);
        if (null != selectedFields) {
            jsonObjectBuilder.set(JSON_SELECTED_FIELDS, selectedFields.toString(), predicate);
        }
        if (NULL_SNAPSHOT_REVISION != snapshotRevision) {
            jsonObjectBuilder.set(JSON_SNAPSHOT_REVISION, snapshotRevision, predicate);
        }
    }

    @Override
    public RetrieveThing setDittoHeaders(final DittoHeaders dittoHeaders) {
        return getBuilder(thingId, dittoHeaders)
                .withSelectedFields(selectedFields)
                .withSnapshotRevision(snapshotRevision)
                .build();
    }

    /**
     * Returns the requested revision of the Thing's persistence snapshot.
     *
     * @return the revision or an empty Optional.
     */
    @Nonnull
    public Optional<Long> getSnapshotRevision() {
        if (NULL_SNAPSHOT_REVISION != snapshotRevision) {
            return Optional.of(snapshotRevision);
        }
        return Optional.empty();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, selectedFields, snapshotRevision);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final RetrieveThing that = (RetrieveThing) obj;
        return that.canEqual(this)
                && Objects.equals(thingId, that.thingId)
                && Objects.equals(selectedFields, that.selectedFields)
                && snapshotRevision == that.snapshotRevision
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveThing;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", selectedFields="
                + selectedFields + ", snapshotRevision=" + snapshotRevision + "]";
    }

    /**
     * A mutable builder with a fluent API for an <em>immutable</em> {@code RetrieveThing}.
     *
     */
    @NotThreadSafe
    public static final class Builder {

        private final String thingId;
        private final DittoHeaders dittoHeaders;
        @Nullable private JsonFieldSelector selectedFields;
        private long snapshotRevision;

        private Builder(final String theThingId, final DittoHeaders theDittoHeaders) {
            thingId = theThingId;
            dittoHeaders = theDittoHeaders;
            selectedFields = null;
            snapshotRevision = NULL_SNAPSHOT_REVISION;
        }

        /**
         * Sets the fields of the JSON representation of the Thing to retrieve.
         *
         * @param fieldSelector the selected JSON fields.
         * @return this builder instance to allow Method Chaining.
         */
        public Builder withSelectedFields(@Nullable final JsonFieldSelector fieldSelector) {
            selectedFields = fieldSelector;
            return this;
        }

        /**
         * Sets the revision of the persistence snapshot of the Thing to retrieve.
         *
         * @param snapshotRevision the revision.
         * @return this builder instance to allow Method Chaining.
         */
        public Builder withSnapshotRevision(final long snapshotRevision) {
            this.snapshotRevision = snapshotRevision;
            return this;
        }

        public RetrieveThing build() {
            return new RetrieveThing(this);
        }

    }

}
