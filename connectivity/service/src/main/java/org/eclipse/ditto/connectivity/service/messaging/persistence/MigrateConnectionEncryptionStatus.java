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

import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Command to query the current status of an encryption migration.
 * <p>
 * Returns the progress from the migration progress collection, including whether a migration
 * is currently running, the current phase, and document counts.
 */
@Immutable
@JsonParsableCommand(typePrefix = MigrateConnectionEncryptionStatus.TYPE_PREFIX,
        name = MigrateConnectionEncryptionStatus.NAME)
public final class MigrateConnectionEncryptionStatus extends AbstractCommand<MigrateConnectionEncryptionStatus> {

    static final String TYPE_PREFIX = "connectivity.commands:";
    static final String NAME = "migrateEncryptionStatus";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private static final String RESOURCE_TYPE = "connectivity";

    private MigrateConnectionEncryptionStatus(final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
    }

    /**
     * Creates a new {@code MigrateConnectionEncryptionStatus} command.
     *
     * @param dittoHeaders the headers of the command.
     * @return the command.
     */
    public static MigrateConnectionEncryptionStatus of(final DittoHeaders dittoHeaders) {
        return new MigrateConnectionEncryptionStatus(dittoHeaders);
    }

    /**
     * Creates a new {@code MigrateConnectionEncryptionStatus} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     */
    public static MigrateConnectionEncryptionStatus fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<MigrateConnectionEncryptionStatus>(TYPE, jsonObject)
                .deserialize(() -> of(dittoHeaders));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        // no payload fields
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
    public MigrateConnectionEncryptionStatus setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(dittoHeaders);
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
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof MigrateConnectionEncryptionStatus;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
