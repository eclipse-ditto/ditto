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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.signaltransformer.SignalTransformer;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;

import com.typesafe.config.Config;

/**
 * Transforms a ModifyThing and a MergeThing command into a CreateThing if the thing does not exist already.
 */
public final class ModifyToCreateThingTransformer implements SignalTransformer {

    private final ThingExistenceChecker existenceChecker;

    ModifyToCreateThingTransformer(final ActorSystem actorSystem, final Config config) {
        this(new ThingExistenceChecker(actorSystem));
    }

    ModifyToCreateThingTransformer(final ThingExistenceChecker existenceChecker) {
        this.existenceChecker = existenceChecker;
    }

    @Override
    public CompletionStage<Signal<?>> apply(final Signal<?> signal) {
        return calculateInputParams(signal)
                .map(input -> existenceChecker.checkExistence(input.thingModifyCommand())
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

    private static Optional<InputParams> calculateInputParams(final Signal<?> signal) {
        if (signal instanceof ModifyThing modifyThing) {
            return Optional.of(new InputParams(modifyThing,
                    modifyThing.getThing(),
                    modifyThing.getInitialPolicy().orElse(null),
                    modifyThing.getPolicyIdOrPlaceholder().orElse(null)
            ));
        } else if (signal instanceof MergeThing mergeThing && mergeThing.getPath().isEmpty()) {
            final JsonObject mergeThingObject = mergeThing.getEntity().orElseGet(mergeThing::getValue).asObject();
            return Optional.of(new InputParams(mergeThing,
                    ThingsModelFactory.newThing(mergeThingObject),
                    mergeThing.getInitialPolicy().orElse(null),
                    mergeThing.getPolicyIdOrPlaceholder().orElse(null)
            ));
        } else {
            return Optional.empty();
        }
    }

    private record InputParams(ThingModifyCommand<?> thingModifyCommand, Thing thing,
            @Nullable JsonObject initialPolicy,
            @Nullable String policyIdOrPlaceholder) {}
}
