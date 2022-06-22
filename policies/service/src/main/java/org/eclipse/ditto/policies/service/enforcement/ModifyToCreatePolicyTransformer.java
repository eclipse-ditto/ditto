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
package org.eclipse.ditto.policies.service.enforcement;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.enforcement.pre.ExistenceChecker;
import org.eclipse.ditto.policies.enforcement.pre.PreEnforcer;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;

import akka.actor.ActorSystem;

/**
 * Transforms a ModifyThing into a CreateThing if the thing does not exist already.
 */
public final class ModifyToCreatePolicyTransformer implements PreEnforcer {

    private final ExistenceChecker existenceChecker;

    ModifyToCreatePolicyTransformer(final ActorSystem actorSystem) {
        this.existenceChecker = ExistenceChecker.get(actorSystem);
    }

    @Override
    public CompletionStage<Signal<?>> apply(final Signal<?> signal) {
        if (signal instanceof ModifyPolicy modifyPolicy) {
            return existenceChecker.checkExistence(signal)
                    .thenApply(exists -> {
                        if (Boolean.FALSE.equals(exists)) {
                            return CreatePolicy.of(modifyPolicy.getPolicy(), modifyPolicy.getDittoHeaders());
                        } else {
                            return modifyPolicy;
                        }
                    });
        } else {
            return CompletableFuture.completedStage(signal);
        }
    }

}
