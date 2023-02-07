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
package org.eclipse.ditto.things.service.enforcement;

import java.util.List;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.policies.enforcement.AbstractEnforcementReloaded;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Authorizes {@link Signal}s and filters {@link CommandResponse}s related to things by applying different included
 * {@link ThingEnforcementStrategy}s.
 */
public final class ThingEnforcement extends AbstractEnforcementReloaded<Signal<?>, CommandResponse<?>> {

    private final List<ThingEnforcementStrategy> enforcementStrategies;

    public ThingEnforcement(final ActorRef policiesShardRegion, final ActorSystem actorSystem,
            final EnforcementConfig enforcementConfig) {

        enforcementStrategies = List.of(
                new LiveSignalEnforcement(),
                new ThingCommandEnforcement(actorSystem, policiesShardRegion, enforcementConfig),
                new StreamRequestingCommandEnforcement()
        );
    }

    @Override
    public CompletionStage<Signal<?>> authorizeSignal(final Signal<?> signal, final PolicyEnforcer policyEnforcer) {
        final Signal<?> adaptedSignal;
        if (signal instanceof CreateThing createThing) {
            final Thing thingWithBestEffortPolicyId = createThing.getThing()
                    .toBuilder()
                    .setPolicyId(policyEnforcer.getPolicy().flatMap(Policy::getEntityId).orElse(null))
                    .build();
            adaptedSignal = CreateThing.of(
                    thingWithBestEffortPolicyId,
                    createThing.getInitialPolicy().orElse(null),
                    createThing.getPolicyIdOrPlaceholder().orElse(null),
                    createThing.getDittoHeaders()
            );
        } else {
            adaptedSignal = signal;
        }
        return enforcementStrategies.stream()
                .filter(strategy -> strategy.isApplicable(adaptedSignal))
                .findFirst()
                .map(strategy -> strategy.getEnforcement().authorizeSignal(adaptedSignal, policyEnforcer))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported signal to perform authorizeSignal: " + adaptedSignal
                ));
    }

    @Override
    public CompletionStage<Signal<?>> authorizeSignalWithMissingEnforcer(final Signal<?> signal) {
        return enforcementStrategies.stream()
                .filter(strategy -> strategy.isApplicable(signal))
                .findFirst()
                .map(strategy -> strategy.getEnforcement().authorizeSignalWithMissingEnforcer(signal))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported signal to perform authorizeSignalWithMissingEnforcer: " + signal
                ));
    }

    @Override
    public boolean shouldFilterCommandResponse(final CommandResponse<?> commandResponse) {
        return enforcementStrategies.stream()
                .filter(strategy -> strategy.responseIsApplicable(commandResponse))
                .findFirst()
                .map(strategy -> strategy.getEnforcement().shouldFilterCommandResponse(commandResponse))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported command response to perform shouldFilterCommandResponse: " + commandResponse
                ));
    }

    @Override
    public CompletionStage<CommandResponse<?>> filterResponse(final CommandResponse<?> commandResponse,
            final PolicyEnforcer policyEnforcer) {
        return enforcementStrategies.stream()
                .filter(strategy -> strategy.responseIsApplicable(commandResponse))
                .findFirst()
                .map(strategy -> strategy.getEnforcement().filterResponse(commandResponse, policyEnforcer))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported command response to perform filterResponse: " + commandResponse
                ));
    }
}
