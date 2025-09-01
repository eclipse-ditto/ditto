/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.validation;

import static org.eclipse.ditto.wot.validation.InternalValidation.success;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.wot.model.ThingModel;

import com.networknt.schema.JsonSchema;

final class InternalThingValidation {

    private final InternalValidation internalValidation;

    InternalThingValidation(@Nullable final Cache<JsonSchemaCacheKey, JsonSchema> jsonSchemaCache) {
        internalValidation = new InternalValidation(jsonSchemaCache);
    }

    CompletableFuture<Void> enforceThingAttributes(final ThingModel thingModel,
            final Attributes attributes,
            final boolean forbidNonModeledAttributes,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        return thingModel.getProperties()
                .map(tdProperties -> {
                    final String containerNamePlural = "Thing's attributes";
                    final CompletableFuture<Void> ensureRequiredPropertiesStage =
                            internalValidation.ensureRequiredProperties(thingModel,
                                    tdProperties,
                                    attributes,
                                    containerNamePlural,
                                    "Thing's attribute",
                                    resourcePath,
                                    false,
                                    context
                            );

                    final CompletableFuture<Void> ensureOnlyDefinedPropertiesStage;
                    if (forbidNonModeledAttributes) {
                        ensureOnlyDefinedPropertiesStage = internalValidation.ensureOnlyDefinedProperties(thingModel,
                                tdProperties,
                                attributes,
                                containerNamePlural,
                                false,
                                context
                        );
                    } else {
                        ensureOnlyDefinedPropertiesStage = success();
                    }

                    final CompletableFuture<Void> validatePropertiesStage =
                            internalValidation.validateProperties(thingModel,
                                    tdProperties,
                                    attributes,
                                    true,
                                    containerNamePlural,
                                    resourcePath,
                                    false,
                                    context
                            );

                    return CompletableFuture.allOf(
                            ensureRequiredPropertiesStage,
                            ensureOnlyDefinedPropertiesStage,
                            validatePropertiesStage
                    );
                }).orElseGet(InternalValidation::success);
    }

    CompletableFuture<Void> enforceThingAttribute(final ThingModel thingModel,
            final JsonPointer attributePath,
            final JsonValue attributeValue,
            final boolean forbidNonModeledAttributes,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        return thingModel.getProperties()
                .map(tdProperties -> {
                    final Attributes attributes = Attributes.newBuilder().set(attributePath, attributeValue).build();
                    final CompletableFuture<Void> ensureOnlyDefinedPropertiesStage;
                    if (forbidNonModeledAttributes) {
                        ensureOnlyDefinedPropertiesStage = internalValidation.ensureOnlyDefinedProperties(thingModel,
                                tdProperties,
                                attributes,
                                "Thing's attributes",
                                false,
                                context
                        );
                    } else {
                        ensureOnlyDefinedPropertiesStage = success();
                    }

                    final CompletableFuture<Void> validatePropertiesStage =
                            internalValidation.validateProperty(thingModel,
                                    tdProperties,
                                    attributePath,
                                    true,
                                    attributeValue,
                                    "Thing's attribute <" + attributePath + ">", resourcePath,
                                    false,
                                    Set.of(),
                                    context
                            );

                    return CompletableFuture.allOf(
                            ensureOnlyDefinedPropertiesStage,
                            validatePropertiesStage
                    );
                }).orElseGet(InternalValidation::success);
    }

    CompletableFuture<Void> enforcePresenceOfRequiredPropertiesUponThingLevelDeletion(
            final ThingModel thingModel,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        return internalValidation.enforcePresenceOfRequiredPropertiesUponDeletion(
                thingModel,
                resourcePath,
                false,
                Set.of(),
                "all Thing attributes",
                "Thing attribute",
                context
        );
    }

    CompletableFuture<Void> enforceThingActionPayload(final ThingModel thingModel,
            final String messageSubject,
            @Nullable final JsonValue payload,
            final boolean forbidNonModeledInboxMessages,
            final JsonPointer resourcePath,
            final boolean isInput,
            final ValidationContext context,
            final Executor executor
    ) {
        final CompletableFuture<Void> firstStage;
        if (isInput && forbidNonModeledInboxMessages) {
            firstStage = internalValidation.ensureOnlyDefinedActions(thingModel.getActions().orElse(null),
                    messageSubject,
                    "Thing's",
                    context
            );
        } else {
            firstStage = success();
        }
        return firstStage.thenComposeAsync(unused ->
                        internalValidation.enforceActionPayload(thingModel, messageSubject, payload, resourcePath, isInput,
                                "Thing's action <" + messageSubject + "> " + (isInput ? "input" : "output"),
                                context
                        ),
                executor
        );
    }

    CompletableFuture<Void> enforceThingEventPayload(final ThingModel thingModel,
            final String messageSubject,
            @Nullable final JsonValue payload,
            final boolean forbidNonModeledOutboxMessages,
            final JsonPointer resourcePath,
            final ValidationContext context,
            final Executor executor
    ) {
        final CompletableFuture<Void> firstStage;
        if (forbidNonModeledOutboxMessages) {
            firstStage = internalValidation.ensureOnlyDefinedEvents(thingModel.getEvents().orElse(null),
                    messageSubject, "Thing's", context);
        } else {
            firstStage = success();
        }
        return firstStage.thenComposeAsync(unused ->
                        internalValidation.enforceEventPayload(thingModel, messageSubject, payload, resourcePath,
                                "Thing's event <" + messageSubject + "> data", context
                        ),
                executor
        );
    }

}
