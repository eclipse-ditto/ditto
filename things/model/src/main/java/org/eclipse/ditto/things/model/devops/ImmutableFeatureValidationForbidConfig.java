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
 * Immutable value object representing feature-level forbid configuration for WoT validation.
 *
 * @since 3.8.0
 */
@Immutable
final class ImmutableFeatureValidationForbidConfig implements FeatureValidationForbidConfig {

    private static final JsonFieldDefinition<Boolean> FEATURE_DESCRIPTION_DELETION =
            JsonFactory.newBooleanFieldDefinition("feature-description-deletion", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> NON_MODELED_FEATURES =
            JsonFactory.newBooleanFieldDefinition("non-modeled-features", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> NON_MODELED_PROPERTIES =
            JsonFactory.newBooleanFieldDefinition("non-modeled-properties", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> NON_MODELED_DESIRED_PROPERTIES =
            JsonFactory.newBooleanFieldDefinition("non-modeled-desired-properties", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> NON_MODELED_INBOX_MESSAGES =
            JsonFactory.newBooleanFieldDefinition("non-modeled-inbox-messages", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> NON_MODELED_OUTBOX_MESSAGES =
            JsonFactory.newBooleanFieldDefinition("non-modeled-outbox-messages", FieldType.REGULAR, JsonSchemaVersion.V_2);

    @Nullable private final Boolean featureDescriptionDeletion;
    @Nullable private final Boolean nonModeledFeatures;
    @Nullable private final Boolean nonModeledProperties;
    @Nullable private final Boolean nonModeledDesiredProperties;
    @Nullable private final Boolean nonModeledInboxMessages;
    @Nullable private final Boolean nonModeledOutboxMessages;

    private ImmutableFeatureValidationForbidConfig(
            @Nullable final Boolean featureDescriptionDeletion,
            @Nullable final Boolean nonModeledFeatures,
            @Nullable final Boolean nonModeledProperties,
            @Nullable final Boolean nonModeledDesiredProperties,
            @Nullable final Boolean nonModeledInboxMessages,
            @Nullable final Boolean nonModeledOutboxMessages) {
        this.featureDescriptionDeletion = featureDescriptionDeletion;
        this.nonModeledFeatures = nonModeledFeatures;
        this.nonModeledProperties = nonModeledProperties;
        this.nonModeledDesiredProperties = nonModeledDesiredProperties;
        this.nonModeledInboxMessages = nonModeledInboxMessages;
        this.nonModeledOutboxMessages = nonModeledOutboxMessages;
    }

    /**
     * Creates a new instance of {@code ImmutableFeatureForbidConfig}.
     *
     * @param featureDescriptionDeletion whether to forbid feature description deletion
     * @param nonModeledFeatures whether to forbid non-modeled features
     * @param nonModeledProperties whether to forbid non-modeled properties
     * @param nonModeledDesiredProperties whether to forbid non-modeled desired properties
     * @param nonModeledInboxMessages whether to forbid non-modeled inbox messages
     * @param nonModeledOutboxMessages whether to forbid non-modeled outbox messages
     * @return a new instance with the specified values
     */
    public static ImmutableFeatureValidationForbidConfig of(
            @Nullable final Boolean featureDescriptionDeletion,
            @Nullable final Boolean nonModeledFeatures,
            @Nullable final Boolean nonModeledProperties,
            @Nullable final Boolean nonModeledDesiredProperties,
            @Nullable final Boolean nonModeledInboxMessages,
            @Nullable final Boolean nonModeledOutboxMessages) {
        return new ImmutableFeatureValidationForbidConfig(
                featureDescriptionDeletion,
                nonModeledFeatures,
                nonModeledProperties,
                nonModeledDesiredProperties,
                nonModeledInboxMessages,
                nonModeledOutboxMessages);
    }

    @Override
    public Optional<Boolean> isFeatureDescriptionDeletion() {
        return Optional.ofNullable(featureDescriptionDeletion);
    }

    @Override
    public Optional<Boolean> isNonModeledFeatures() {
        return Optional.ofNullable(nonModeledFeatures);
    }

    @Override
    public Optional<Boolean> isNonModeledProperties() {
        return Optional.ofNullable(nonModeledProperties);
    }

    @Override
    public Optional<Boolean> isNonModeledDesiredProperties() {
        return Optional.ofNullable(nonModeledDesiredProperties);
    }

    @Override
    public Optional<Boolean> isNonModeledInboxMessages() {
        return Optional.ofNullable(nonModeledInboxMessages);
    }

    @Override
    public Optional<Boolean> isNonModeledOutboxMessages() {
        return Optional.ofNullable(nonModeledOutboxMessages);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        isFeatureDescriptionDeletion().ifPresent(value -> builder.set(FEATURE_DESCRIPTION_DELETION, value));
        isNonModeledFeatures().ifPresent(value -> builder.set(NON_MODELED_FEATURES, value));
        isNonModeledProperties().ifPresent(value -> builder.set(NON_MODELED_PROPERTIES, value));
        isNonModeledDesiredProperties().ifPresent(value -> builder.set(NON_MODELED_DESIRED_PROPERTIES, value));
        isNonModeledInboxMessages().ifPresent(value -> builder.set(NON_MODELED_INBOX_MESSAGES, value));
        isNonModeledOutboxMessages().ifPresent(value -> builder.set(NON_MODELED_OUTBOX_MESSAGES, value));
        return builder.build();
    }

    /**
     * Creates a new instance of {@code ImmutableFeatureForbidConfig} from a JSON object.
     * The JSON object should contain the following fields:
     * <ul>
     *     <li>{@code featureDescriptionDeletion} (optional): Whether to forbid feature description deletion</li>
     *     <li>{@code nonModeledFeatures} (optional): Whether to forbid non-modeled features</li>
     *     <li>{@code nonModeledProperties} (optional): Whether to forbid non-modeled properties</li>
     *     <li>{@code nonModeledDesiredProperties} (optional): Whether to forbid non-modeled desired properties</li>
     *     <li>{@code nonModeledInboxMessages} (optional): Whether to forbid non-modeled inbox messages</li>
     *     <li>{@code nonModeledOutboxMessages} (optional): Whether to forbid non-modeled outbox messages</li>
     * </ul>
     *
     * @param jsonObject the JSON object to create the configuration from
     * @return a new instance created from the JSON object
     * @throws NullPointerException if {@code jsonObject} is {@code null}
     */
    public static ImmutableFeatureValidationForbidConfig fromJson(final JsonObject jsonObject) {
        final Boolean featureDescriptionDeletion = jsonObject.getValue(FEATURE_DESCRIPTION_DELETION)
                .orElse(null);

        final Boolean nonModeledFeatures = jsonObject.getValue(NON_MODELED_FEATURES)
                .orElse(null);

        final Boolean nonModeledProperties = jsonObject.getValue(NON_MODELED_PROPERTIES)
                .orElse(null);

        final Boolean nonModeledDesiredProperties = jsonObject.getValue(NON_MODELED_DESIRED_PROPERTIES)
                .orElse(null);

        final Boolean nonModeledInboxMessages = jsonObject.getValue(NON_MODELED_INBOX_MESSAGES)
                .orElse(null);

        final Boolean nonModeledOutboxMessages = jsonObject.getValue(NON_MODELED_OUTBOX_MESSAGES)
                .orElse(null);

        return of(
                featureDescriptionDeletion,
                nonModeledFeatures,
                nonModeledProperties,
                nonModeledDesiredProperties,
                nonModeledInboxMessages,
                nonModeledOutboxMessages
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
        final ImmutableFeatureValidationForbidConfig that = (ImmutableFeatureValidationForbidConfig) o;
        return Objects.equals(featureDescriptionDeletion, that.featureDescriptionDeletion) &&
                Objects.equals(nonModeledFeatures, that.nonModeledFeatures) &&
                Objects.equals(nonModeledProperties, that.nonModeledProperties) &&
                Objects.equals(nonModeledDesiredProperties, that.nonModeledDesiredProperties) &&
                Objects.equals(nonModeledInboxMessages, that.nonModeledInboxMessages) &&
                Objects.equals(nonModeledOutboxMessages, that.nonModeledOutboxMessages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                featureDescriptionDeletion,
                nonModeledFeatures,
                nonModeledProperties,
                nonModeledDesiredProperties,
                nonModeledInboxMessages,
                nonModeledOutboxMessages);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "featureDescriptionDeletion=" + featureDescriptionDeletion +
                ", nonModeledFeatures=" + nonModeledFeatures +
                ", nonModeledProperties=" + nonModeledProperties +
                ", nonModeledDesiredProperties=" + nonModeledDesiredProperties +
                ", nonModeledInboxMessages=" + nonModeledInboxMessages +
                ", nonModeledOutboxMessages=" + nonModeledOutboxMessages +
                "]";
    }
} 