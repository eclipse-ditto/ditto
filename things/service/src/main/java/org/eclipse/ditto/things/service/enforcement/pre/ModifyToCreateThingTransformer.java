/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.enforcement.pre;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.pattern.Patterns;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.signaltransformer.SignalTransformer;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.WithThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;

import com.typesafe.config.Config;

/**
 * Transforms a ModifyThing and a MergeThing command into a CreateThing if the thing does not exist already.
 */
public final class ModifyToCreateThingTransformer implements SignalTransformer {

    private static final Duration LOCAL_ASK_TIMEOUT = Duration.ofSeconds(5);

    @SuppressWarnings("unused")
    ModifyToCreateThingTransformer(final ActorSystem actorSystem, final Config config) {
        // no-op
    }

    @Override
    public CompletionStage<Signal<?>> apply(final Signal<?> signal, final ActorRef thisRef) {

        return calculateInputParams(signal)
                .map(input -> checkExistence((WithThingId) signal, thisRef)
                        .thenApply(exists -> {
                            if (Boolean.FALSE.equals(exists)) {
                                final var newThing = input.thing().toBuilder()
                                        .setId(input.thingModifyCommand().getEntityId())
                                        .build();
                                return CreateThing.of(newThing, input.initialPolicy(), input.policyIdOrPlaceholder(),
                                        input.thingModifyCommand().getDittoHeaders());
                            } else {
                                return (Signal<?>) input.thingModifyCommand();
                            }
                        })
                ).orElse(CompletableFuture.completedFuture(signal));
    }

    private static CompletionStage<Boolean> checkExistence(final WithThingId signal, final ActorRef thisRef) {
        return Patterns.ask(thisRef, SudoRetrieveThing.of(signal.getEntityId(),
                        JsonFieldSelector.newInstance(
                                Thing.JsonFields.POLICY_ID.getPointer(), Thing.JsonFields.REVISION.getPointer()
                        ),
                        DittoHeaders.empty()
                ), LOCAL_ASK_TIMEOUT
        ).thenApply(ModifyToCreateThingTransformer::handleSudoRetrieveThingResponse);
    }

    private static Boolean handleSudoRetrieveThingResponse(final Object response) {
        if (response instanceof SudoRetrieveThingResponse) {
            return Boolean.TRUE;
        } else if (response instanceof ThingNotAccessibleException) {
            return Boolean.FALSE;
        } else {
            throw new IllegalStateException("expect SudoRetrieveThingResponse, got: " + response);
        }
    }

    private static Optional<InputParams> calculateInputParams(final Signal<?> signal) {
        switch (signal) {
            case ModifyThing modifyThing -> {
                return Optional.of(new InputParams(modifyThing,
                        modifyThing.getThing(),
                        modifyThing.getInitialPolicy().orElse(null),
                        modifyThing.getPolicyIdOrPlaceholder().orElse(null)
                ));
            }
            case MergeThing mergeThing when mergeThing.getPath().isEmpty() -> {
                final JsonObject mergeThingObject = mergeThing.getEntity().orElseGet(mergeThing::getValue).asObject();
                final JsonObject nullFilteredMergeThingObject = filterOutNullValues(mergeThingObject);
                return Optional.of(new InputParams(mergeThing,
                        ThingsModelFactory.newThing(nullFilteredMergeThingObject),
                        mergeThing.getInitialPolicy().orElse(null),
                        mergeThing.getPolicyIdOrPlaceholder().orElse(null)
                ));
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    private static JsonObject filterOutNullValues(final JsonObject mergeThingObject) {
        return mergeThingObject.stream()
                .filter(entry -> !entry.getValue().isNull())
                .map(field -> {
                    if (field.getValue().isObject()) {
                        return JsonField.newInstance(field.getKey(), filterOutNullValues(field.getValue().asObject()));
                    } else if (field.getValue().isArray()) {
                        return JsonField.newInstance(field.getKey(), field.getValue().asArray().stream()
                                .filter(value -> !value.isNull())
                                .map(value -> value.isObject() ? filterOutNullValues(value.asObject()) : value)
                                .collect(JsonCollectors.valuesToArray()));
                    } else {
                        return field;
                    }
                })
                .collect(JsonCollectors.fieldsToObject());
    }

    private record InputParams(ThingModifyCommand<?> thingModifyCommand, Thing thing,
                               @Nullable JsonObject initialPolicy,
                               @Nullable String policyIdOrPlaceholder) {}
}
