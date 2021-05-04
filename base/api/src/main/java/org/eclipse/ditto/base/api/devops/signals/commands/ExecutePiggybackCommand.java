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
package org.eclipse.ditto.base.api.devops.signals.commands;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;

/**
 * Command to execute an arbitrary wrapped Command which is included "piggyback" in this {@link DevOpsCommand}.
 * <p>Example JSON to send:</p>
 * <pre>
 * {@code {
        "headers": {
            "auth-subjects": ["i.dont.care.about.the.namespace:i-bims"],
            "schemaVersion": 2
        },
        "targetActorSelection": "/user/gatewayRoot/proxy",
        "piggybackCommand": {
            "type": "things.commands:retrieveThing",
            "thingId": "com.acme:foobar1"
        }
    }
 * }
 * </pre>
 */
@Immutable
@JsonParsableCommand(typePrefix = DevOpsCommand.TYPE_PREFIX, name = ExecutePiggybackCommand.NAME)
public final class ExecutePiggybackCommand extends AbstractDevOpsCommand<ExecutePiggybackCommand> {

    /**
     * Name of the command.
     */
    public static final String NAME = "executePiggybackCommand";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    public static final JsonFieldDefinition<String> JSON_TARGET_ACTORSELECTION =
            JsonFactory.newStringFieldDefinition("targetActorSelection", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);
    public static final JsonFieldDefinition<JsonObject> JSON_PIGGYBACK_COMMAND =
            JsonFactory.newJsonObjectFieldDefinition("piggybackCommand", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private final String targetActorSelection;
    private final JsonObject piggybackCommand;

    private ExecutePiggybackCommand(@Nullable final String serviceName, @Nullable final String instance,
            final String targetActorSelection, final JsonObject piggybackCommand, final DittoHeaders dittoHeaders) {
        super(TYPE, serviceName, instance, dittoHeaders);
        this.targetActorSelection = requireNonNull(targetActorSelection, "The targetActorSelection must not be null!");
        this.piggybackCommand = requireNonNull(piggybackCommand, "The piggybackCommand must not be null!");
    }

    /**
     * Returns a new instance of {@code ExecutePiggybackCommand}.
     *
     * @param serviceName the service name to which to send the DevOpsCommand.
     * @param instance the instance index of the serviceName to which to send the DevOpsCommand.
     * @param piggybackCommand the command to execute.
     * @param targetActorSelection the ActorSelection path as string where to send the piggybackCommand to.
     * @param dittoHeaders the headers of the request.
     * @return a new ExecutePiggybackCommand command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ExecutePiggybackCommand of(@Nullable final String serviceName, @Nullable final String instance,
            final String targetActorSelection, final JsonObject piggybackCommand, final DittoHeaders dittoHeaders) {
        return new ExecutePiggybackCommand(serviceName, instance, targetActorSelection, piggybackCommand, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ExecutePiggybackCommand}.
     *
     * @param serviceName the service name to which to send the DevOpsCommand.
     * @param piggybackCommand the command to execute.
     * @param targetActorSelection the ActorSelection path as string where to send the piggybackCommand to.
     * @param dittoHeaders the headers of the request.
     * @return a new ExecutePiggybackCommand command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ExecutePiggybackCommand of(@Nullable final String serviceName, final String targetActorSelection,
            final JsonObject piggybackCommand, final DittoHeaders dittoHeaders) {
        return new ExecutePiggybackCommand(serviceName, null, targetActorSelection, piggybackCommand, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ExecutePiggybackCommand}.
     *
     * @param targetActorSelection the ActorSelection path as string where to send the piggybackCommand to.
     * @param piggybackCommand the command to execute.
     * @param dittoHeaders the headers of the request.
     * @return a new ExecutePiggybackCommand command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ExecutePiggybackCommand of(final String targetActorSelection, final JsonObject piggybackCommand,
            final DittoHeaders dittoHeaders) {
        return new ExecutePiggybackCommand(null, null, targetActorSelection, piggybackCommand, dittoHeaders);
    }

    /**
     * Creates a new {@code ExecutePiggybackCommand} from a JSON string.
     *
     * @param jsonString contains the data of the ExecutePiggybackCommand command.
     * @param dittoHeaders the headers of the request.
     * @return the ExecutePiggybackCommand command which is based on the data of {@code jsonString}.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ExecutePiggybackCommand fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ExecutePiggybackCommand} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ExecutePiggybackCommand fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ExecutePiggybackCommand>(TYPE, jsonObject).deserialize(() -> {
            final String serviceName = jsonObject.getValue(DevOpsCommand.JsonFields.JSON_SERVICE_NAME).orElse(null);
            final String instance = jsonObject.getValue(DevOpsCommand.JsonFields.JSON_INSTANCE).orElse(null);
            final String targetActorSelection = jsonObject.getValueOrThrow(JSON_TARGET_ACTORSELECTION);
            final JsonObject piggybackCommand = jsonObject.getValueOrThrow(JSON_PIGGYBACK_COMMAND);

            return of(serviceName, instance, targetActorSelection, piggybackCommand, dittoHeaders);
        });
    }

    /**
     * @return the ActorSelection path as string where to send the piggybackCommand to.
     */
    public String getTargetActorSelection() {
        return targetActorSelection;
    }

    /**
     * @return the {@code Command} as JsonObject to execute.
     */
    public JsonObject getPiggybackCommand() {
        return piggybackCommand;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ExecutePiggybackCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getServiceName().orElse(null), getInstance().orElse(null), targetActorSelection, piggybackCommand,
                dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_TARGET_ACTORSELECTION, targetActorSelection, predicate);
        jsonObjectBuilder.set(JSON_PIGGYBACK_COMMAND, piggybackCommand, predicate);
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExecutePiggybackCommand that = (ExecutePiggybackCommand) o;
        return that.canEqual(this) && Objects.equals(targetActorSelection, that.targetActorSelection)
                && Objects.equals(piggybackCommand, that.piggybackCommand) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ExecutePiggybackCommand;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), targetActorSelection, piggybackCommand);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", targetActorSelection=" + targetActorSelection +
                ", piggybackCommand=" + piggybackCommand + "]";
    }

}
