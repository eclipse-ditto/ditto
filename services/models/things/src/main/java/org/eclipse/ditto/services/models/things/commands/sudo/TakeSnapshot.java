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
package org.eclipse.ditto.services.models.things.commands.sudo;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Command to trigger taking snapshot of a thing.
 */
@Immutable
@AllValuesAreNonnullByDefault
public final class TakeSnapshot extends AbstractCommand<TakeSnapshot> implements SudoCommand<TakeSnapshot>, WithId {

    /**
     * Name of the "Take Snapshot" command.
     */
    public static final String NAME = "takeSnapshot";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_ID =
            JsonFactory.newStringFieldDefinition("id", FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

    private final String id;

    /**
     * Returns a Command for taking snapshot from the entity (e.g. thing, policy, etc) with the given ID.
     *
     * @param id the ID of  entity, that should take the snapshot.
     * @param dittoHeaders the headers of the command.
     * @return a {@code TakeSnapshot} command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static TakeSnapshot of(final String id, final DittoHeaders dittoHeaders) {
        return new TakeSnapshot(id, dittoHeaders);
    }

    /**
     * Creates a new {@code TakeSnapshot} command from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return a {@code TakeSnapshot} command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static TakeSnapshot fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code TakeSnapshot} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return a {@code TakeSnapshot} command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static TakeSnapshot fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return of(jsonObject.getValueOrThrow(JSON_ID), dittoHeaders);
    }

    private TakeSnapshot(final String id, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public TakeSnapshot setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(id, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_ID, id, predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id);
    }


    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof TakeSnapshot;
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
        final TakeSnapshot that = (TakeSnapshot) obj;
        return that.canEqual(this) && Objects.equals(id, that.id) && super.equals(that);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", id=" + id + "]";
    }

}
