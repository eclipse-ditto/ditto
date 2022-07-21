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
package org.eclipse.ditto.policies.service.enforcement.pre;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.signaltransformer.SignalTransformer;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Transforms a ModifyPolicy into a CreatePolicy if the thing does not exist already.
 */
public final class ModifyToCreatePolicyTransformer implements SignalTransformer {

    private final PolicyExistenceChecker existenceChecker;

    ModifyToCreatePolicyTransformer(final ActorSystem actorSystem, final Config config) {
        this(new PolicyExistenceChecker(actorSystem));
    }

    ModifyToCreatePolicyTransformer(final PolicyExistenceChecker existenceChecker) {
        this.existenceChecker = existenceChecker;
    }

    @Override
    public CompletionStage<Signal<?>> apply(final Signal<?> signal) {
        if (signal instanceof ModifyPolicy modifyPolicy) {
            return existenceChecker.checkExistence(modifyPolicy)
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
