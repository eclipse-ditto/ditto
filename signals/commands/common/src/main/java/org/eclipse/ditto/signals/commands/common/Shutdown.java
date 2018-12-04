/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.common;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

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
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * Command for shutting down arbitrary parts of the system.
 * Each recipient decides whether it accepts the provided reason of this command or if it ignores the command.
 */
@Immutable
public final class Shutdown extends CommonCommand<Shutdown> {

    /**
     * The name of the {@code Shutdown} command.
     */
    static final String NAME = "shutdown";

    /**
     * The type of the {@code Shutdown} command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final ShutdownReason reason;

    private Shutdown(final ShutdownReason theReason, final DittoHeaders dittoHeaders) {
        super(TYPE, Category.MODIFY, dittoHeaders);
        reason = checkNotNull(theReason, "ShutdownReason");
    }

    /**
     * Returns an instance of {@code Shutdown}.
     *
     * @param shutdownReason the reason for the returned command.
     * @param dittoHeaders the headers of the returned command.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Shutdown getInstance(final ShutdownReason shutdownReason, final DittoHeaders dittoHeaders) {
        return new Shutdown(shutdownReason, dittoHeaders);
    }

    /**
     * Creates a new {@code Shutdown} from the given JSON object.
     *
     * @param jsonObject the JSON object of which the Shutdown is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain
     * {@link org.eclipse.ditto.signals.commands.common.Shutdown.JsonFields#REASON}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static Shutdown fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<Shutdown>(TYPE, jsonObject).deserialize(
                () -> getInstance(ShutdownReasonFactory.fromJson(jsonObject.getValueOrThrow(JsonFields.REASON)),
                        dittoHeaders));
    }

    /**
     * Returns the reason for issuing this command.
     *
     * @return the reason of this command.
     */
    public ShutdownReason getReason() {
        return reason;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(JsonFields.REASON, reason.toJson(schemaVersion, predicate), schemaVersion.and(predicate));
    }

    @Override
    public Shutdown setDittoHeaders(final DittoHeaders dittoHeaders) {
        if (Objects.equals(getDittoHeaders(), dittoHeaders)) {
            return this;
        }
        return new Shutdown(reason, dittoHeaders);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final Shutdown that = (Shutdown) o;
        return that.canEqual(this) && Objects.equals(reason, that.reason) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof Shutdown;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), reason);
    }

    /**
     * This class contains definitions for all specific fields of a {@code ShutdownCommand}'s JSON representation.
     */
    @Immutable
    public static final class JsonFields extends Command.JsonFields {

        public static final JsonFieldDefinition<JsonObject> REASON = JsonFactory.newJsonObjectFieldDefinition("reason",
                FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", reason=" + reason +
                "]";
    }

}
