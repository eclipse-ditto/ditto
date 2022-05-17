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
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.utils.pubsub.LiveSignalPub;
import org.eclipse.ditto.policies.enforcement.AbstractEnforcementReloaded;
import org.eclipse.ditto.policies.enforcement.CreationRestrictionEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.service.persistence.actors.ResponseReceiverCache;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Authorizes {@link Signal}s and filters {@link CommandResponse}s related to things by applying different included
 * {@link ThingEnforcementStrategy}s.
 */
public final class ThingEnforcement extends AbstractEnforcementReloaded<Signal<?>, CommandResponse<?>> {

    private final List<ThingEnforcementStrategy> enforcementStrategies;
    private final ActorRef policiesShardRegion;
    private final EnforcementConfig enforcementConfig;

    public ThingEnforcement(final ActorSystem actorSystem,
            final ActorRef policiesShardRegion,
            final CreationRestrictionEnforcer creationRestrictionEnforcer,
            final EnforcementConfig enforcementConfig,
            final LiveSignalPub liveSignalPub,
            final ResponseReceiverCache responseReceiverCache) {

        this.policiesShardRegion = policiesShardRegion;
        this.enforcementConfig = enforcementConfig;

        enforcementStrategies = List.of(
                new LiveSignalEnforcement(),
                new ThingCommandEnforcement(
                        actorSystem,
                        policiesShardRegion,
                        creationRestrictionEnforcer,
                        enforcementConfig,
                        liveSignalPub,
                        responseReceiverCache
                )
        );
    }

    /**
     * @return the policies shard region which this things specific enforcement received during construction.
     */
    public ActorRef getPoliciesShardRegion() {
        return policiesShardRegion;
    }

    /**
     * @return the EnforcementConfig which this things specific enforcement received during construction.
     */
    public EnforcementConfig getEnforcementConfig() {
        return enforcementConfig;
    }

    @Override
    public void registerPolicyEnforcerLoader(
            final Function<PolicyId, CompletionStage<PolicyEnforcer>> policyEnforcerLoader) {
        super.registerPolicyEnforcerLoader(policyEnforcerLoader);
        enforcementStrategies.forEach(strategy -> strategy.getEnforcement()
                .registerPolicyEnforcerLoader(policyEnforcerLoader)
        );
    }

    @Override
    public void registerPolicyInjectionConsumer(final Consumer<Policy> policyInjectionConsumer) {
        super.registerPolicyInjectionConsumer(policyInjectionConsumer);
        enforcementStrategies.forEach(strategy -> strategy.getEnforcement()
                .registerPolicyInjectionConsumer(policyInjectionConsumer)
        );
    }

    @Override
    public CompletionStage<Signal<?>> authorizeSignal(final Signal<?> signal, final PolicyEnforcer policyEnforcer) {
        return enforcementStrategies.stream()
                .filter(strategy -> strategy.isApplicable(signal))
                .findFirst()
                .map(strategy -> strategy.getEnforcement().authorizeSignal(signal, policyEnforcer))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported signal to perform authorizeSignal: " + signal
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
