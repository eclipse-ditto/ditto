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
 * Command to abort a currently running encryption migration.
 * <p>
 * If no migration is running, responds with an error. Otherwise cancels the running stream
 * after the current batch, saves progress, and responds with the progress at the time of abort.
 */
@Immutable
@JsonParsableCommand(typePrefix = MigrateConnectionEncryptionAbort.TYPE_PREFIX,
        name = MigrateConnectionEncryptionAbort.NAME)
public final class MigrateConnectionEncryptionAbort extends AbstractCommand<MigrateConnectionEncryptionAbort> {

    static final String TYPE_PREFIX = "connectivity.commands:";
    static final String NAME = "migrateEncryptionAbort";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private static final String RESOURCE_TYPE = "connectivity";

    private MigrateConnectionEncryptionAbort(final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
    }

    /**
     * Creates a new {@code MigrateConnectionEncryptionAbort} command.
     *
     * @param dittoHeaders the headers of the command.
     * @return the command.
     */
    public static MigrateConnectionEncryptionAbort of(final DittoHeaders dittoHeaders) {
        return new MigrateConnectionEncryptionAbort(dittoHeaders);
    }

    /**
     * Creates a new {@code MigrateConnectionEncryptionAbort} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     */
    public static MigrateConnectionEncryptionAbort fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<MigrateConnectionEncryptionAbort>(TYPE, jsonObject)
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
        return Category.MODIFY;
    }

    @Override
    public MigrateConnectionEncryptionAbort setDittoHeaders(final DittoHeaders dittoHeaders) {
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
        return other instanceof MigrateConnectionEncryptionAbort;
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
