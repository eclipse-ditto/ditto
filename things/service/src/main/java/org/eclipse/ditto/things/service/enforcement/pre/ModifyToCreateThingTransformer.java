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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.signaltransformer.SignalTransformer;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Transforms a ModifyThing into a CreateThing if the thing does not exist already.
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
        if (signal instanceof ModifyThing modifyThing) {
            return existenceChecker.checkExistence(modifyThing)
                    .thenApply(exists -> {
                        if (Boolean.FALSE.equals(exists)) {
                            final JsonObject initialPolicy = modifyThing.getInitialPolicy().orElse(null);
                            final String policyIdOrPlaceholder = modifyThing.getPolicyIdOrPlaceholder().orElse(null);
                            final var newThing = modifyThing.getThing().toBuilder()
                                    .setId(modifyThing.getEntityId())
                                    .build();
                            return CreateThing.of(
                                    newThing,
                                    initialPolicy,
                                    policyIdOrPlaceholder,
                                    modifyThing.getDittoHeaders()
                            );
                        } else {
                            return modifyThing;
                        }
                    });
        } else {
            return CompletableFuture.completedStage(signal);
        }
    }

}
