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
package org.eclipse.ditto.services.utils.pubsub.api;

import java.io.NotSerializableException;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Command from Publisher to Subscriber to publish a signal to local subscribers.
 */
@JsonParsableCommand(typePrefix = PublishSignal.TYPE_PREFIX, name = PublishSignal.NAME)
public final class PublishSignal extends AbstractCommand<PublishSignal> {

    /**
     * Type prefix of this command.
     */
    public static final String TYPE_PREFIX = "pubsub.command:";

    /**
     * Name of this command.
     */
    public static final String NAME = "publish";

    private static final String TYPE = TYPE_PREFIX + NAME;

    private final Signal<?> signal;
    private final List<String> groups;

    private PublishSignal(final Signal<?> signal, final List<String> groups) {
        super(TYPE, signal.getDittoHeaders(), Category.MODIFY);
        this.signal = signal;
        this.groups = groups;
    }

    /**
     * Create a PublishSignal command from a signal and the groups it is published to.
     *
     * @param signal the signal to publish.
     * @param groups the groups where the signal is published to.
     * @return the command to do it.
     */
    public static PublishSignal of(final Signal<?> signal, final List<String> groups) {
        return new PublishSignal(signal, groups);
    }

    /**
     * Deserialize this command.
     *
     * @param jsonObject the JSON representation of this command.
     * @param dittoHeaders the Ditto headers of the underlying signal. Used by reflection. Do not delete.
     * @param parseInnerJson function to parse the inner JSON.
     * @return the deserialized command.
     */
    public static PublishSignal fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders,
            final JsonParsable.ParseInnerJson parseInnerJson) {

        try {
            final Signal<?> signal =
                    (Signal<?>) parseInnerJson.parseInnerJson(jsonObject.getValueOrThrow(JsonFields.SIGNAL));
            final List<String> groups = jsonObject.getValueOrThrow(JsonFields.GROUPS)
                    .stream()
                    .map(JsonValue::asString)
                    .collect(Collectors.toList());
            return new PublishSignal(signal, groups);
        } catch (final NotSerializableException e) {
            throw new JsonParseException(e.getMessage());
        }
    }

    /**
     * @return the signal to be published.
     */
    public Signal<?> getSignal() {
        return signal;
    }

    /**
     * @return the groups in which the signal is to be published.
     */
    public List<String> getGroups() {
        return groups;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(JsonFields.SIGNAL, signalToJson(schemaVersion, predicate))
                .set(JsonFields.GROUPS, groups.stream().map(JsonValue::of).collect(JsonCollectors.valuesToArray()));
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
    public PublishSignal setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new PublishSignal(signal.setDittoHeaders(dittoHeaders), groups);
    }

    @Override
    public EntityId getEntityId() {
        return signal.getEntityId();
    }

    @Override
    public JsonPointer getResourcePath() {
        return signal.getResourcePath();
    }

    @Override
    public String getResourceType() {
        return signal.getResourceType();
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof PublishSignal) {
            final PublishSignal that = (PublishSignal) other;
            return Objects.equals(signal, that.signal) && Objects.equals(groups, that.groups);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(signal, groups);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[signal=" + signal + ", groups=" + groups + "]";
    }

    private JsonObject signalToJson(final JsonSchemaVersion jsonSchemaVersion, final Predicate<JsonField> predicate) {
        final JsonObject signalJson = signal.toJson(jsonSchemaVersion, predicate);
        if (signalJson.contains(Command.JsonFields.TYPE.getPointer())) {
            return signalJson;
        } else {
            return signalJson.toBuilder().set(Command.JsonFields.TYPE, signal.getType()).build();
        }
    }

    private static final class JsonFields {

        private static final JsonFieldDefinition<JsonObject> SIGNAL =
                JsonFactory.newJsonObjectFieldDefinition("signal");

        private static final JsonFieldDefinition<JsonArray> GROUPS = JsonFactory.newJsonArrayFieldDefinition("groups");
    }
}
