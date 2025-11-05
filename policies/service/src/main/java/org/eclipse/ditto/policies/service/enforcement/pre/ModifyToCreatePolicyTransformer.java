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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.pattern.Patterns;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.signaltransformer.SignalTransformer;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.model.WithPolicyId;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;

import com.typesafe.config.Config;

/**
 * Transforms a ModifyPolicy into a CreatePolicy if the thing does not exist already.
 */
public final class ModifyToCreatePolicyTransformer implements SignalTransformer {

    private static final Duration LOCAL_ASK_TIMEOUT = Duration.ofSeconds(5);

    @SuppressWarnings("unused")
    ModifyToCreatePolicyTransformer(final ActorSystem actorSystem, final Config config) {
    }

    @Override
    public CompletionStage<Signal<?>> apply(final Signal<?> signal, final ActorRef thisRef) {
        if (signal instanceof ModifyPolicy modifyPolicy) {
            return checkExistence(modifyPolicy, thisRef)
                    .thenApply(exists -> {
                        if (Boolean.FALSE.equals(exists)) {
                            return CreatePolicy.of(modifyPolicy.getPolicy(), modifyPolicy.getDittoHeaders());
                        } else {
                            return modifyPolicy;
                        }
                    });
        } else {
            return CompletableFuture.completedFuture(signal);
        }
    }

    private static CompletionStage<Boolean> checkExistence(final WithPolicyId signal, final ActorRef thisRef) {
        return Patterns.ask(thisRef, SudoRetrievePolicy.of(signal.getEntityId(),
                        DittoHeaders.empty()
                ), LOCAL_ASK_TIMEOUT
        ).thenApply(ModifyToCreatePolicyTransformer::handleSudoRetrievePolicyResponse);
    }

    private static Boolean handleSudoRetrievePolicyResponse(final Object response) {
        if (response instanceof SudoRetrievePolicyResponse) {
            return Boolean.TRUE;
        } else if (response instanceof PolicyNotAccessibleException) {
            return Boolean.FALSE;
        } else {
            throw new IllegalStateException("expect SudoRetrievePolicyResponse, got: " + response);
        }
    }

}
