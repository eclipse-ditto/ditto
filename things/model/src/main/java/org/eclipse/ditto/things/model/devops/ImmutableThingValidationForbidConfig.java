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
 * Immutable value object representing thing-level forbid configuration for WoT validation.
 * <p>
 * This class encapsulates configuration settings for forbidding thing-level validation rules.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
final class ImmutableThingValidationForbidConfig implements ThingValidationForbidConfig {


    private static final JsonFieldDefinition<Boolean> THING_DESCRIPTION_DELETION =
            JsonFactory.newBooleanFieldDefinition("thingDescriptionDeletion", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> NON_MODELED_ATTRIBUTES =
            JsonFactory.newBooleanFieldDefinition("nonModeledAttributes", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> NON_MODELED_INBOX_MESSAGES =
            JsonFactory.newBooleanFieldDefinition("nonModeledInboxMessages", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> NON_MODELED_OUTBOX_MESSAGES =
            JsonFactory.newBooleanFieldDefinition("nonModeledOutboxMessages", FieldType.REGULAR, JsonSchemaVersion.V_2);

    @Nullable private final Boolean thingDescriptionDeletion;
    @Nullable private final Boolean nonModeledAttributes;
    @Nullable private final Boolean nonModeledInboxMessages;
    @Nullable private final Boolean nonModeledOutboxMessages;

    private ImmutableThingValidationForbidConfig(
            @Nullable final Boolean thingDescriptionDeletion,
            @Nullable final Boolean nonModeledAttributes,
            @Nullable final Boolean nonModeledInboxMessages,
            @Nullable final Boolean nonModeledOutboxMessages) {
        this.thingDescriptionDeletion = thingDescriptionDeletion;
        this.nonModeledAttributes = nonModeledAttributes;
        this.nonModeledInboxMessages = nonModeledInboxMessages;
        this.nonModeledOutboxMessages = nonModeledOutboxMessages;
    }

    /**
     * Creates a new instance of {@code ImmutableThingForbidConfig}.
     *
     * @param thingDescriptionDeletion whether to forbid thing description deletion
     * @param nonModeledAttributes whether to forbid non-modeled attributes
     * @param nonModeledInboxMessages whether to forbid non-modeled inbox messages
     * @param nonModeledOutboxMessages whether to forbid non-modeled outbox messages
     * @return a new instance with the specified values
     */
    public static ImmutableThingValidationForbidConfig of(
            @Nullable final Boolean thingDescriptionDeletion,
            @Nullable final Boolean nonModeledAttributes,
            @Nullable final Boolean nonModeledInboxMessages,
            @Nullable final Boolean nonModeledOutboxMessages) {
        return new ImmutableThingValidationForbidConfig(
                thingDescriptionDeletion,
                nonModeledAttributes,
                nonModeledInboxMessages,
                nonModeledOutboxMessages);
    }

    @Override
    public Optional<Boolean> isThingDescriptionDeletion() {
        return Optional.ofNullable(thingDescriptionDeletion);
    }

    @Override
    public Optional<Boolean> isNonModeledAttributes() {
        return Optional.ofNullable(nonModeledAttributes);
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
        isThingDescriptionDeletion().ifPresent(value -> builder.set(THING_DESCRIPTION_DELETION, value));
        isNonModeledAttributes().ifPresent(value -> builder.set(NON_MODELED_ATTRIBUTES, value));
        isNonModeledInboxMessages().ifPresent(value -> builder.set(NON_MODELED_INBOX_MESSAGES, value));
        isNonModeledOutboxMessages().ifPresent(value -> builder.set(NON_MODELED_OUTBOX_MESSAGES, value));
        return builder.build();
    }

    /**
     * Creates a new instance of {@code ImmutableThingForbidConfig} from a JSON object.
     * The JSON object should contain the following fields:
     * <ul>
     *     <li>{@code thingDescriptionDeletion} (optional): Whether to forbid thing description deletion</li>
     *     <li>{@code nonModeledThings} (optional): Whether to forbid non-modeled things</li>
     *     <li>{@code nonModeledAttributes} (optional): Whether to forbid non-modeled attributes</li>
     *     <li>{@code nonModeledInboxMessages} (optional): Whether to forbid non-modeled inbox messages</li>
     *     <li>{@code nonModeledOutboxMessages} (optional): Whether to forbid non-modeled outbox messages</li>
     * </ul>
     *
     * @param jsonObject the JSON object to create the configuration from
     * @return a new instance created from the JSON object
     * @throws NullPointerException if {@code jsonObject} is {@code null}
     */
    public static ImmutableThingValidationForbidConfig fromJson(final JsonObject jsonObject) {
        final Boolean thingDescriptionDeletion = jsonObject.getValue(THING_DESCRIPTION_DELETION)
                .orElse(null);

        final Boolean nonModeledAttributes = jsonObject.getValue(NON_MODELED_ATTRIBUTES)
                .orElse(null);

        final Boolean nonModeledInboxMessages = jsonObject.getValue(NON_MODELED_INBOX_MESSAGES)
                .orElse(null);

        final Boolean nonModeledOutboxMessages = jsonObject.getValue(NON_MODELED_OUTBOX_MESSAGES)
                .orElse(null);

        return of(
                thingDescriptionDeletion,
                nonModeledAttributes,
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
        final ImmutableThingValidationForbidConfig that = (ImmutableThingValidationForbidConfig) o;
        return Objects.equals(thingDescriptionDeletion, that.thingDescriptionDeletion) &&
                Objects.equals(nonModeledAttributes, that.nonModeledAttributes) &&
                Objects.equals(nonModeledInboxMessages, that.nonModeledInboxMessages) &&
                Objects.equals(nonModeledOutboxMessages, that.nonModeledOutboxMessages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                thingDescriptionDeletion,
                nonModeledAttributes,
                nonModeledInboxMessages,
                nonModeledOutboxMessages);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingDescriptionDeletion=" + thingDescriptionDeletion +
                ", nonModeledAttributes=" + nonModeledAttributes +
                ", nonModeledInboxMessages=" + nonModeledInboxMessages +
                ", nonModeledOutboxMessages=" + nonModeledOutboxMessages +
                "]";
    }
} 