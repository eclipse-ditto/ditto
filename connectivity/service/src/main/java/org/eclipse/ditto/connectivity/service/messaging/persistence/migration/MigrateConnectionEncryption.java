/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.persistence.migration;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Command to trigger re-encryption of all persisted connection data (snapshots and journal events)
 * from the old encryption key to the new encryption key.
 * <p>
 * This command is sent via the DevOps piggyback endpoint to the {@link org.eclipse.ditto.connectivity.service.messaging.persistence.migration.EncryptionMigrationActor}.
 */
@Immutable
@JsonParsableCommand(typePrefix = MigrateConnectionEncryption.TYPE_PREFIX, name = MigrateConnectionEncryption.NAME)
public final class MigrateConnectionEncryption extends AbstractCommand<MigrateConnectionEncryption> {

    static final String TYPE_PREFIX = "connectivity.commands:";
    static final String NAME = "migrateEncryption";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<Boolean> JSON_DRY_RUN =
            JsonFactory.newBooleanFieldDefinition("dryRun", JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<Boolean> JSON_RESUME =
            JsonFactory.newBooleanFieldDefinition("resume", JsonSchemaVersion.V_2);

    private static final String RESOURCE_TYPE = "connectivity";

    private final boolean dryRun;
    private final boolean resume;

    private MigrateConnectionEncryption(final boolean dryRun, final boolean resume,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.dryRun = dryRun;
        this.resume = resume;
    }

    /**
     * Creates a new {@code MigrateConnectionEncryption} command.
     *
     * @param dryRun whether to only count affected documents without making changes.
     * @param resume whether to resume from last saved progress.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     */
    public static MigrateConnectionEncryption of(final boolean dryRun, final boolean resume,
            final DittoHeaders dittoHeaders) {
        return new MigrateConnectionEncryption(dryRun, resume, dittoHeaders);
    }

    /**
     * Creates a new {@code MigrateConnectionEncryption} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     */
    public static MigrateConnectionEncryption fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<MigrateConnectionEncryption>(TYPE, jsonObject).deserialize(
                () -> {
                    final boolean dryRun = jsonObject.getValue(JSON_DRY_RUN).orElse(false);
                    final boolean resume = jsonObject.getValue(JSON_RESUME).orElse(false);
                    return of(dryRun, resume, dittoHeaders);
                });
    }

    /**
     * Returns whether this is a dry-run (count only, no changes).
     *
     * @return {@code true} if dry-run.
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * Returns whether to resume from last saved progress.
     *
     * @return {@code true} if resuming.
     */
    public boolean isResume() {
        return resume;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_DRY_RUN, dryRun, predicate);
        jsonObjectBuilder.set(JSON_RESUME, resume, predicate);
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
    public MigrateConnectionEncryption setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(dryRun, resume, dittoHeaders);
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
        return other instanceof MigrateConnectionEncryption;
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
        final MigrateConnectionEncryption that = (MigrateConnectionEncryption) o;
        return dryRun == that.dryRun && resume == that.resume;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), dryRun, resume);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", dryRun=" + dryRun +
                ", resume=" + resume +
                "]";
    }

}
