/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.devops;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Immutable value object representing thing-level enforce configuration for WoT validation.
 * <p>
 * This class encapsulates configuration settings for enforcing thing-level validation rules.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
final class ImmutableThingValidationEnforceConfig implements ThingValidationEnforceConfig {

    private static final JsonFieldDefinition<Boolean> THING_DESCRIPTION_MODIFICATION =
            JsonFactory.newBooleanFieldDefinition("thingDescriptionModification", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> ATTRIBUTES =
            JsonFactory.newBooleanFieldDefinition("attributes", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> INBOX_MESSAGES_INPUT =
            JsonFactory.newBooleanFieldDefinition("inboxMessagesInput", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> INBOX_MESSAGES_OUTPUT =
            JsonFactory.newBooleanFieldDefinition("inboxMessagesOutput", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> OUTBOX_MESSAGES =
            JsonFactory.newBooleanFieldDefinition("outboxMessages", FieldType.REGULAR, JsonSchemaVersion.V_2);

    @Nullable private final Boolean thingDescriptionModification;
    @Nullable private final Boolean attributes;
    @Nullable private final Boolean inboxMessagesInput;
    @Nullable private final Boolean inboxMessagesOutput;
    @Nullable private final Boolean outboxMessages;

    private ImmutableThingValidationEnforceConfig(
            @Nullable final Boolean thingDescriptionModification,
            @Nullable final Boolean attributes,
            @Nullable final Boolean inboxMessagesInput,
            @Nullable final Boolean inboxMessagesOutput,
            @Nullable final Boolean outboxMessages) {
        this.thingDescriptionModification = thingDescriptionModification;
        this.attributes = attributes;
        this.inboxMessagesInput = inboxMessagesInput;
        this.inboxMessagesOutput = inboxMessagesOutput;
        this.outboxMessages = outboxMessages;
    }

    /**
     * Creates a new instance of {@code ImmutableThingEnforceConfig}.
     *
     * @param thingDescriptionModification whether to enforce thing description modification
     * @param attributes whether to enforce attributes
     * @param inboxMessagesInput whether to enforce inbox messages input
     * @param inboxMessagesOutput whether to enforce inbox messages output
     * @param outboxMessages whether to enforce outbox messages
     * @return a new instance with the specified values
     */
    public static ImmutableThingValidationEnforceConfig of(
            @Nullable final Boolean thingDescriptionModification,
            @Nullable final Boolean attributes,
            @Nullable final Boolean inboxMessagesInput,
            @Nullable final Boolean inboxMessagesOutput,
            @Nullable final Boolean outboxMessages) {
        return new ImmutableThingValidationEnforceConfig(
                thingDescriptionModification,
                attributes,
                inboxMessagesInput,
                inboxMessagesOutput,
                outboxMessages);
    }

    @Override
    public Optional<Boolean> isThingDescriptionModification() {
        return Optional.ofNullable(thingDescriptionModification);
    }

    @Override
    public Optional<Boolean> isAttributes() {
        return Optional.ofNullable(attributes);
    }

    @Override
    public Optional<Boolean> isInboxMessagesInput() {
        return Optional.ofNullable(inboxMessagesInput);
    }

    @Override
    public Optional<Boolean> isInboxMessagesOutput() {
        return Optional.ofNullable(inboxMessagesOutput);
    }

    @Override
    public Optional<Boolean> isOutboxMessages() {
        return Optional.ofNullable(outboxMessages);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        isThingDescriptionModification().ifPresent(value -> builder.set(THING_DESCRIPTION_MODIFICATION, value));
        isAttributes().ifPresent(value -> builder.set(ATTRIBUTES, value));
        isInboxMessagesInput().ifPresent(value -> builder.set(INBOX_MESSAGES_INPUT, value));
        isInboxMessagesOutput().ifPresent(value -> builder.set(INBOX_MESSAGES_OUTPUT, value));
        isOutboxMessages().ifPresent(value -> builder.set(OUTBOX_MESSAGES, value));
        return builder.build();
    }

    /**
     * Creates a new instance of {@code ImmutableThingEnforceConfig} from a JSON object.
     * The JSON object should contain the following fields:
     * <ul>
     *     <li>{@code thingDescriptionModification} (optional): Whether to enforce thing description modification</li>
     *     <li>{@code presenceOfModeledThings} (optional): Whether to enforce presence of modeled things</li>
     *     <li>{@code properties} (optional): Whether to enforce properties</li>
     *     <li>{@code desiredProperties} (optional): Whether to enforce desired properties</li>
     *     <li>{@code inboxMessagesInput} (optional): Whether to enforce inbox messages input</li>
     *     <li>{@code inboxMessagesOutput} (optional): Whether to enforce inbox messages output</li>
     *     <li>{@code outboxMessages} (optional): Whether to enforce outbox messages</li>
     * </ul>
     *
     * @param jsonObject the JSON object to create the configuration from
     * @return a new instance created from the JSON object
     * @throws NullPointerException if {@code jsonObject} is {@code null}
     * @throws IllegalArgumentException if the JSON object is invalid
     */
    public static ImmutableThingValidationEnforceConfig fromJson(final JsonObject jsonObject) {
        final Boolean thingDescriptionModification = jsonObject.getValue(THING_DESCRIPTION_MODIFICATION)
                .orElse(null);

        final Boolean attributes = jsonObject.getValue(ATTRIBUTES)
                .orElse(null);


        final Boolean inboxMessagesInput = jsonObject.getValue(INBOX_MESSAGES_INPUT)
                .orElse(null);

        final Boolean inboxMessagesOutput = jsonObject.getValue(INBOX_MESSAGES_OUTPUT)
                .orElse(null);

        final Boolean outboxMessages = jsonObject.getValue(OUTBOX_MESSAGES)
                .orElse(null);

        return of(
                thingDescriptionModification,
                attributes,
                inboxMessagesInput,
                inboxMessagesOutput,
                outboxMessages
        );
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableThingValidationEnforceConfig that = (ImmutableThingValidationEnforceConfig) o;
        return Objects.equals(thingDescriptionModification, that.thingDescriptionModification) &&
                Objects.equals(attributes, that.attributes) &&
                Objects.equals(inboxMessagesInput, that.inboxMessagesInput) &&
                Objects.equals(inboxMessagesOutput, that.inboxMessagesOutput) &&
                Objects.equals(outboxMessages, that.outboxMessages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                thingDescriptionModification,
                attributes,
                inboxMessagesInput,
                inboxMessagesOutput,
                outboxMessages);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingDescriptionModification=" + thingDescriptionModification +
                ", attributes=" + attributes +
                ", inboxMessagesInput=" + inboxMessagesInput +
                ", inboxMessagesOutput=" + inboxMessagesOutput +
                ", outboxMessages=" + outboxMessages +
                "]";
    }
} 