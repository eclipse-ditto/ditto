/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.cleanup;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * Command for starting the cleanup of persistence actors.
 */
@Immutable
@JsonParsableCommand(typePrefix = Cleanup.TYPE_PREFIX, name = Cleanup.NAME)
public class Cleanup extends AbstractCommand<Cleanup> implements CleanupCommand<Cleanup> {

    /**
     * The name of the {@code Cleanup} command.
     */
    static final String NAME = "cleanup";

    /**
     * The type of the {@code Cleanup} command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final String entityId;

    private Cleanup(final String entityId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.entityId = ConditionChecker.checkNotNull(entityId, "entityId");
    }

    public static Cleanup of(final String entityId, final DittoHeaders dittoHeaders) {
        return new Cleanup(entityId, dittoHeaders);
    }

    @Override
    public String getEntityId() {
        return entityId;
    }

    @Override
    public Cleanup setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new Cleanup(entityId, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(CleanupCommand.JsonFields.ENTITY_ID, entityId, predicate);
    }

    /**
     * Creates a new {@code Cleanup} command from the given JSON object.
     *
     * @param jsonObject the JSON object of which the Cleanup is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain
     * {@link CleanupCommand.JsonFields#ENTITY_ID}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static Cleanup fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<Cleanup>(TYPE, jsonObject).deserialize(
                () -> of(jsonObject.getValueOrThrow(CleanupCommand.JsonFields.ENTITY_ID), dittoHeaders));
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof Cleanup;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final Cleanup cleanup = (Cleanup) o;
        return entityId.equals(cleanup.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), entityId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", entityId=" + entityId +
                "]";
    }

}