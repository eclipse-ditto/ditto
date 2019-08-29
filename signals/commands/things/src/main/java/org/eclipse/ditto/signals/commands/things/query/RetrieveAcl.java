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
package org.eclipse.ditto.signals.commands.things.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * Command which retrieves the ACL (all of them or a specific one) of a {@code Thing} based on the passed in ID.
 */
@Immutable
@JsonParsableCommand(typePrefix = RetrieveAcl.TYPE_PREFIX, name = RetrieveAcl.NAME)
public final class RetrieveAcl extends AbstractCommand<RetrieveAcl> implements ThingQueryCommand<RetrieveAcl> {

    /**
     * Name of this command.
     */
    public static final String NAME = "retrieveAcl";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final ThingId thingId;

    private RetrieveAcl(final ThingId thingId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.thingId = checkNotNull(thingId, "Thing ID");
    }

    /**
     * Returns a command for retrieving the ACL of a Thing with the given ID.
     *
     * @param thingId the ID of a single Thing whose ACL will be retrieved by this command.
     * @param dittoHeaders the headers of the command.
     * @return a Command for retrieving ACL of the Thing with the {@code thingId} as its ID which is readable from the
     * passed authorization context.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     * @deprecated Thing ID is now typed. Use
     * {@link #of(org.eclipse.ditto.model.things.ThingId, org.eclipse.ditto.model.base.headers.DittoHeaders)}
     * instead.
     */
    @Deprecated
    public static RetrieveAcl of(final String thingId, final DittoHeaders dittoHeaders) {
        return of(ThingId.of(thingId), dittoHeaders);
    }

    /**
     * Returns a command for retrieving the ACL of a Thing with the given ID.
     *
     * @param thingId the ID of a single Thing whose ACL will be retrieved by this command.
     * @param dittoHeaders the headers of the command.
     * @return a Command for retrieving ACL of the Thing with the {@code thingId} as its ID which is readable from the
     * passed authorization context.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static RetrieveAcl of(final ThingId thingId, final DittoHeaders dittoHeaders) {
        return new RetrieveAcl(thingId, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveAcl} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.base.entity.id.RegexPatterns#ID_REGEX}.
     */
    public static RetrieveAcl fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveAcl} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.base.entity.id.RegexPatterns#ID_REGEX}.
     */
    public static RetrieveAcl fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RetrieveAcl>(TYPE, jsonObject).deserialize(() -> {
            final String extractedThingId = jsonObject.getValueOrThrow(ThingQueryCommand.JsonFields.JSON_THING_ID);
            final ThingId thingId = ThingId.of(extractedThingId);
            return of(thingId, dittoHeaders);
        });
    }

    @Override
    public ThingId getThingEntityId() {
        return thingId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/acl");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingQueryCommand.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
    }

    @Override
    public RetrieveAcl setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, dittoHeaders);
    }

    /**
     * RetrieveAcl is only available in JsonSchemaVersion V_1.
     *
     * @return the supported JsonSchemaVersions of RetrieveAcl.
     */
    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_1};
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId);
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final RetrieveAcl that = (RetrieveAcl) obj;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId) && super.equals(that);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + "]";
    }

}
