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
 * Immutable value object representing feature-level enforce configuration for WoT validation.

 *
 * @since 3.8.0
 */
@Immutable
final class ImmutableFeatureValidationEnforceConfig implements FeatureValidationEnforceConfig {


    private static final JsonFieldDefinition<Boolean> FEATURE_DESCRIPTION_MODIFICATION =
            JsonFactory.newBooleanFieldDefinition("featureDescriptionModification", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> PRESENCE_OF_MODELED_FEATURES =
            JsonFactory.newBooleanFieldDefinition("presenceOfModeledFeatures", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> PROPERTIES =
            JsonFactory.newBooleanFieldDefinition("properties", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> DESIRED_PROPERTIES =
            JsonFactory.newBooleanFieldDefinition("desiredProperties", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> INBOX_MESSAGES_INPUT =
            JsonFactory.newBooleanFieldDefinition("inboxMessagesInput", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> INBOX_MESSAGES_OUTPUT =
            JsonFactory.newBooleanFieldDefinition("inboxMessagesOutput", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> OUTBOX_MESSAGES =
            JsonFactory.newBooleanFieldDefinition("outboxMessages", FieldType.REGULAR, JsonSchemaVersion.V_2);

    @Nullable private final Boolean featureDescriptionModification;
    @Nullable private final Boolean presenceOfModeledFeatures;
    @Nullable private final Boolean properties;
    @Nullable private final Boolean desiredProperties;
    @Nullable private final Boolean inboxMessagesInput;
    @Nullable private final Boolean inboxMessagesOutput;
    @Nullable private final Boolean outboxMessages;

    private ImmutableFeatureValidationEnforceConfig(
            @Nullable final Boolean featureDescriptionModification,
            @Nullable final Boolean presenceOfModeledFeatures,
            @Nullable final Boolean properties,
            @Nullable final Boolean desiredProperties,
            @Nullable final Boolean inboxMessagesInput,
            @Nullable final Boolean inboxMessagesOutput,
            @Nullable final Boolean outboxMessages) {
        this.featureDescriptionModification = featureDescriptionModification;
        this.presenceOfModeledFeatures = presenceOfModeledFeatures;
        this.properties = properties;
        this.desiredProperties = desiredProperties;
        this.inboxMessagesInput = inboxMessagesInput;
        this.inboxMessagesOutput = inboxMessagesOutput;
        this.outboxMessages = outboxMessages;
    }

    /**
     * Creates a new instance of {@code ImmutableFeatureEnforceConfig}.
     *
     * @param featureDescriptionModification whether to enforce feature description modification
     * @param presenceOfModeledFeatures whether to enforce presence of modeled features
     * @param properties whether to enforce properties
     * @param desiredProperties whether to enforce desired properties
     * @param inboxMessagesInput whether to enforce inbox messages input
     * @param inboxMessagesOutput whether to enforce inbox messages output
     * @param outboxMessages whether to enforce outbox messages
     * @return a new instance with the specified values
     */
    public static ImmutableFeatureValidationEnforceConfig of(
            @Nullable final Boolean featureDescriptionModification,
            @Nullable final Boolean presenceOfModeledFeatures,
            @Nullable final Boolean properties,
            @Nullable final Boolean desiredProperties,
            @Nullable final Boolean inboxMessagesInput,
            @Nullable final Boolean inboxMessagesOutput,
            @Nullable final Boolean outboxMessages) {
        return new ImmutableFeatureValidationEnforceConfig(
                featureDescriptionModification,
                presenceOfModeledFeatures,
                properties,
                desiredProperties,
                inboxMessagesInput,
                inboxMessagesOutput,
                outboxMessages);
    }

    @Override
    public Optional<Boolean> isFeatureDescriptionModification() {
        return Optional.ofNullable(featureDescriptionModification);
    }

    @Override
    public Optional<Boolean> isPresenceOfModeledFeatures() {
        return Optional.ofNullable(presenceOfModeledFeatures);
    }

    @Override
    public Optional<Boolean> isProperties() {
        return Optional.ofNullable(properties);
    }

    @Override
    public Optional<Boolean> isDesiredProperties() {
        return Optional.ofNullable(desiredProperties);
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
        isFeatureDescriptionModification().ifPresent(value -> builder.set(FEATURE_DESCRIPTION_MODIFICATION, value));
        isPresenceOfModeledFeatures().ifPresent(value -> builder.set(PRESENCE_OF_MODELED_FEATURES, value));
        isProperties().ifPresent(value -> builder.set(PROPERTIES, value));
        isDesiredProperties().ifPresent(value -> builder.set(DESIRED_PROPERTIES, value));
        isInboxMessagesInput().ifPresent(value -> builder.set(INBOX_MESSAGES_INPUT, value));
        isInboxMessagesOutput().ifPresent(value -> builder.set(INBOX_MESSAGES_OUTPUT, value));
        isOutboxMessages().ifPresent(value -> builder.set(OUTBOX_MESSAGES, value));
        return builder.build();
    }

    /**
     * Creates a new instance of {@code ImmutableFeatureEnforceConfig} from a JSON object.
     * The JSON object should contain the following fields:
     * <ul>
     *     <li>{@code featureDescriptionModification} (optional): Whether to enforce feature description modification</li>
     *     <li>{@code presenceOfModeledFeatures} (optional): Whether to enforce presence of modeled features</li>
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
     */
    public static ImmutableFeatureValidationEnforceConfig fromJson(final JsonObject jsonObject) {
        final Boolean featureDescriptionModification = jsonObject.getValue(FEATURE_DESCRIPTION_MODIFICATION)
                .orElse(null);

        final Boolean presenceOfModeledFeatures = jsonObject.getValue(PRESENCE_OF_MODELED_FEATURES)
                .orElse(null);

        final Boolean properties = jsonObject.getValue(PROPERTIES)
                .orElse(null);

        final Boolean desiredProperties = jsonObject.getValue(DESIRED_PROPERTIES)
                .orElse(null);

        final Boolean inboxMessagesInput = jsonObject.getValue(INBOX_MESSAGES_INPUT)
                .orElse(null);

        final Boolean inboxMessagesOutput = jsonObject.getValue(INBOX_MESSAGES_OUTPUT)
                .orElse(null);

        final Boolean outboxMessages = jsonObject.getValue(OUTBOX_MESSAGES)
                .orElse(null);

        return of(
                featureDescriptionModification,
                presenceOfModeledFeatures,
                properties,
                desiredProperties,
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
        final ImmutableFeatureValidationEnforceConfig that = (ImmutableFeatureValidationEnforceConfig) o;
        return Objects.equals(featureDescriptionModification, that.featureDescriptionModification) &&
                Objects.equals(presenceOfModeledFeatures, that.presenceOfModeledFeatures) &&
                Objects.equals(properties, that.properties) &&
                Objects.equals(desiredProperties, that.desiredProperties) &&
                Objects.equals(inboxMessagesInput, that.inboxMessagesInput) &&
                Objects.equals(inboxMessagesOutput, that.inboxMessagesOutput) &&
                Objects.equals(outboxMessages, that.outboxMessages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                featureDescriptionModification,
                presenceOfModeledFeatures,
                properties,
                desiredProperties,
                inboxMessagesInput,
                inboxMessagesOutput,
                outboxMessages);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "featureDescriptionModification=" + featureDescriptionModification +
                ", presenceOfModeledFeatures=" + presenceOfModeledFeatures +
                ", properties=" + properties +
                ", desiredProperties=" + desiredProperties +
                ", inboxMessagesInput=" + inboxMessagesInput +
                ", inboxMessagesOutput=" + inboxMessagesOutput +
                ", outboxMessages=" + outboxMessages +
                "]";
    }
} 