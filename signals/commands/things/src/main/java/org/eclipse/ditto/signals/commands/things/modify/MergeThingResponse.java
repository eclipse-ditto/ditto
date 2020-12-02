/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.things.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;

/**
 * Response to a {@link MergeThing} command.
 *
 * @since TODO replace-with-correct-version
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyThingResponse.TYPE)
public class MergeThingResponse extends AbstractCommandResponse<MergeThingResponse>
        implements ThingModifyCommandResponse<MergeThingResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + MergeThing.NAME;

    private final ThingId thingId;
    private final JsonPointer path;
    private final JsonValue value;

    private MergeThingResponse(final ThingId thingId, final JsonPointer path, final JsonValue value,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.path = checkNotNull(path, "path");
        this.value = checkNotNull(value, "value");
    }

    /**
     * TODO
     */
    public static MergeThingResponse modified(final ThingId thingId, final JsonPointer path, final JsonValue value,
            final DittoHeaders dittoHeaders) {
        return new MergeThingResponse(thingId, path, value, dittoHeaders);
    }

    @Override
    public ThingId getThingEntityId() {
        return thingId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return path;
    }

    @Override
    public MergeThingResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return modified(thingId, path, value, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicateParam) {

        final Predicate<JsonField> predicate = schemaVersion.and(predicateParam);
        jsonObjectBuilder.set(MergeThingResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(MergeThingResponse.JsonFields.JSON_PATH, path.toString(), predicate);
        jsonObjectBuilder.set(MergeThingResponse.JsonFields.JSON_VALUE, value, predicate);

    }

    /**
     * TODO javadoc
     */
    static class JsonFields {

        static final JsonFieldDefinition<String> JSON_THING_ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<String> JSON_PATH =
                JsonFactory.newStringFieldDefinition("path", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<JsonValue> JSON_VALUE =
                JsonFactory.newJsonValueFieldDefinition("value", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final MergeThingResponse that = (MergeThingResponse) o;
        return that.canEqual(this) && thingId.equals(that.thingId) &&
                path.equals(that.path) &&
                value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, path, value);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", thingId=" + thingId +
                ", path=" + path +
                ", value=" + value +
                "]";
    }
}
