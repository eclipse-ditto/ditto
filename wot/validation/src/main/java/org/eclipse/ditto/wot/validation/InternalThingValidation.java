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

import static org.eclipse.ditto.wot.validation.InternalValidation.enforceActionPayload;
import static org.eclipse.ditto.wot.validation.InternalValidation.enforceEventPayload;
import static org.eclipse.ditto.wot.validation.InternalValidation.enforcePresenceOfRequiredPropertiesUponDeletion;
import static org.eclipse.ditto.wot.validation.InternalValidation.ensureOnlyDefinedActions;
import static org.eclipse.ditto.wot.validation.InternalValidation.ensureOnlyDefinedEvents;
import static org.eclipse.ditto.wot.validation.InternalValidation.ensureOnlyDefinedProperties;
import static org.eclipse.ditto.wot.validation.InternalValidation.ensureRequiredProperties;
import static org.eclipse.ditto.wot.validation.InternalValidation.success;
import static org.eclipse.ditto.wot.validation.InternalValidation.validateProperties;
import static org.eclipse.ditto.wot.validation.InternalValidation.validateProperty;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.wot.model.ThingModel;

final class InternalThingValidation {

    private InternalThingValidation() {
        throw new AssertionError();
    }

    static CompletableFuture<Void> enforceThingAttributes(final ThingModel thingModel,
            final Attributes attributes,
            final boolean forbidNonModeledAttributes,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        return thingModel.getProperties()
                .map(tdProperties -> {
                    final String containerNamePlural = "Thing's attributes";
                    final CompletableFuture<Void> ensureRequiredPropertiesStage = ensureRequiredProperties(thingModel,
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
                        ensureOnlyDefinedPropertiesStage = ensureOnlyDefinedProperties(thingModel,
                                tdProperties,
                                attributes,
                                containerNamePlural,
                                false,
                                context
                        );
                    } else {
                        ensureOnlyDefinedPropertiesStage = success();
                    }

                    final CompletableFuture<Void> validatePropertiesStage = validateProperties(thingModel,
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

    static CompletableFuture<Void> enforceThingAttribute(final ThingModel thingModel,
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
                        ensureOnlyDefinedPropertiesStage = ensureOnlyDefinedProperties(thingModel,
                                tdProperties,
                                attributes,
                                "Thing's attributes",
                                false,
                                context
                        );
                    } else {
                        ensureOnlyDefinedPropertiesStage = success();
                    }

                    final CompletableFuture<Void> validatePropertiesStage = validateProperty(thingModel,
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

    static CompletableFuture<Void> enforcePresenceOfRequiredPropertiesUponThingLevelDeletion(
            final ThingModel thingModel,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        return enforcePresenceOfRequiredPropertiesUponDeletion(
                thingModel,
                resourcePath,
                false,
                Set.of(),
                "all Thing attributes",
                "Thing attribute",
                context
        );
    }

    static CompletableFuture<Void> enforceThingActionPayload(final ThingModel thingModel,
            final String messageSubject,
            @Nullable final JsonValue payload,
            final boolean forbidNonModeledInboxMessages,
            final JsonPointer resourcePath,
            final boolean isInput,
            final ValidationContext context
    ) {
        final CompletableFuture<Void> firstStage;
        if (isInput && forbidNonModeledInboxMessages) {
            firstStage = ensureOnlyDefinedActions(thingModel.getActions().orElse(null),
                    messageSubject,
                    "Thing's",
                    context
            );
        } else {
            firstStage = success();
        }
        return firstStage.thenCompose(unused ->
                enforceActionPayload(thingModel, messageSubject, payload, resourcePath, isInput,
                        "Thing's action <" + messageSubject + "> " + (isInput ? "input" : "output"),
                        context
                )
        );
    }

    static CompletableFuture<Void> enforceThingEventPayload(final ThingModel thingModel,
            final String messageSubject,
            @Nullable final JsonValue payload,
            final boolean forbidNonModeledOutboxMessages,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        final CompletableFuture<Void> firstStage;
        if (forbidNonModeledOutboxMessages) {
            firstStage = ensureOnlyDefinedEvents(thingModel.getEvents().orElse(null),
                    messageSubject, "Thing's", context);
        } else {
            firstStage = success();
        }
        return firstStage.thenCompose(unused ->
                enforceEventPayload(thingModel, messageSubject, payload, resourcePath,
                        "Thing's event <" + messageSubject + "> data", context
                )
        );
    }

}
