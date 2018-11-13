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
package org.eclipse.ditto.signals.commands.devops;

import static org.eclipse.ditto.model.base.json.FieldType.REGULAR;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_1;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;


/**
 * DevOps command for publishing messages in the Akka cluster.
 * This command is inherently fire-and-forget. It has no response.
 */
@Immutable
public final class PublishCommand extends AbstractCommand<PublishCommand> implements DevOpsCommand<PublishCommand> {

    /**
     * Type of this command.
     */
    public static final String TYPE = DevOpsCommand.TYPE_PREFIX + "publish";

    private static JsonFieldDefinition<String> TOPIC =
            JsonFactory.newStringFieldDefinition("topic", REGULAR, V_1, V_2);

    private static JsonFieldDefinition<JsonObject> PAYLOAD =
            JsonFactory.newJsonObjectFieldDefinition("payload", REGULAR, V_1, V_2);

    private static JsonFieldDefinition<Boolean> IS_GROUP_TOPIC =
            JsonFactory.newBooleanFieldDefinition("isGroupTopic", REGULAR, V_1, V_2);

    private final String topic;
    private final JsonObject payload;

    private final boolean isGroupTopic;

    private PublishCommand(final String topic, final JsonObject payload, final boolean isGroupTopic,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.topic = topic;
        this.payload = payload;
        this.isGroupTopic = isGroupTopic;
    }

    /**
     * Deserialize this command from JSON representation.
     *
     * @param jsonObject the JSON representation.
     * @param dittoHeaders the headers.
     * @return the deserialized command.
     */
    public static PublishCommand fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String topic = jsonObject.getValueOrThrow(TOPIC);
        final JsonObject payload = jsonObject.getValueOrThrow(PAYLOAD);
        final boolean isGroupTopic = jsonObject.getValue(IS_GROUP_TOPIC).orElse(false);
        return new PublishCommand(topic, payload, isGroupTopic, dittoHeaders);
    }

    /**
     * @return the topic to publish at.
     */
    public String getTopic() {
        return topic;
    }

    /**
     * @return the payload to publish.
     */
    public JsonObject getPayload() {
        return payload;
    }

    /**
     * @return whether this should be published to subscribers with group IDs and not to those without.
     */
    public boolean isGroupTopic() {
        return isGroupTopic;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        final Predicate<JsonField> thePredicate = predicate.and(schemaVersion);
        jsonObjectBuilder.set(TOPIC, topic, thePredicate)
                .set(PAYLOAD, payload, thePredicate)
                .set(IS_GROUP_TOPIC, isGroupTopic, thePredicate.and(x -> isGroupTopic));
    }

    @Override
    public Optional<String> getServiceName() {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> getInstance() {
        return Optional.empty();
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public PublishCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new PublishCommand(topic, payload, isGroupTopic, dittoHeaders);
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof PublishCommand) {
            final PublishCommand that = (PublishCommand) o;
            return super.equals(that) &&
                    Objects.equals(topic, that.topic) &&
                    Objects.equals(isGroupTopic, that.isGroupTopic) &&
                    Objects.equals(payload, that.payload);
        } else {
            return false;
        }
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PublishCommand;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), topic, isGroupTopic, payload);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ",topic=" + topic +
                ",isGroupTopic=" + isGroupTopic +
                ",payload=" + payload +
                "]";
    }
}
