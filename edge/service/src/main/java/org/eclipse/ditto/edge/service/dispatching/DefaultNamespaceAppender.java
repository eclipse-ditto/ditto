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
package org.eclipse.ditto.edge.service.dispatching;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.enforcement.config.DefaultEntityCreationConfig;
import org.eclipse.ditto.policies.enforcement.config.EntityCreationConfig;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;

import akka.actor.ActorSystem;
import akka.japi.pf.PFBuilder;
import scala.PartialFunction;

/**
 * Appends the globally configured {@link EntityCreationConfig#getDefaultNamespace() Default namespace} to creation
 * commands which do not include an explicit namespace.
 */
public final class DefaultNamespaceAppender implements SignalTransformer {

    private final String defaultNamespace;
    private final PartialFunction<Signal<?>, Signal<?>> signalTransformer;

    /**
     * Constructs a new instance of DefaultNamespaceAppender extension.
     *
     * @param actorSystem the actor system in which to load the extension.
     */
    public DefaultNamespaceAppender(final ActorSystem actorSystem) {
        final EntityCreationConfig entityCreationConfig = DefaultEntityCreationConfig.of(
                DefaultScopedConfig.dittoScoped(actorSystem.settings().config())
        );
        defaultNamespace = entityCreationConfig.getDefaultNamespace();
        signalTransformer = new PFBuilder<Signal<?>, Signal<?>>()
                .match(CreateThing.class, this::handleCreateThing)
                .match(CreatePolicy.class, this::handleCreatePolicy)
                .matchAny(signal -> signal)
                .build();
    }

    public DefaultNamespaceAppender(final String defaultNamespace) {
        this.defaultNamespace = defaultNamespace;
        signalTransformer = new PFBuilder<Signal<?>, Signal<?>>()
                .match(CreateThing.class, this::handleCreateThing)
                .match(CreatePolicy.class, this::handleCreatePolicy)
                .matchAny(signal -> signal)
                .build();
    }

    @Override
    public CompletionStage<Signal<?>> apply(final Signal<?> signal) {
        return CompletableFuture.completedStage(signalTransformer.apply(signal));
    }

    private CreateThing handleCreateThing(final CreateThing createThing) {
        final Optional<ThingId> providedThingId = createThing.getThing().getEntityId();
        final ThingId namespacedThingId = providedThingId
                .map(thingId -> {
                    if (thingId.getNamespace().isEmpty()) {
                        return ThingId.of(defaultNamespace, thingId.getName());
                    } else {
                        return thingId;
                    }
                })
                .orElseGet(() -> ThingId.inNamespaceWithRandomName(defaultNamespace));
        final Thing thingWithNamespacedId = createThing.getThing().toBuilder().setId(namespacedThingId).build();
        @Nullable final JsonObject initialPolicy = createThing.getInitialPolicy().orElse(null);
        @Nullable final String policyIdOrPlaceholder = createThing.getPolicyIdOrPlaceholder().orElse(null);
        final DittoHeaders dittoHeaders = createThing.getDittoHeaders();
        return CreateThing.of(thingWithNamespacedId, initialPolicy, policyIdOrPlaceholder, dittoHeaders);
    }

    private CreatePolicy handleCreatePolicy(final CreatePolicy createPolicy) {
        final Optional<PolicyId> providedPolicyId = createPolicy.getPolicy().getEntityId();
        final PolicyId namespacedPolicyId = providedPolicyId
                .map(policyId -> {
                    if (policyId.getNamespace().isEmpty()) {
                        return PolicyId.of(defaultNamespace, policyId.getName());
                    } else {
                        return policyId;
                    }
                })
                .orElseGet(() -> PolicyId.inNamespaceWithRandomName(defaultNamespace));
        final Policy policyWithNamespacedId = createPolicy.getPolicy().toBuilder().setId(namespacedPolicyId).build();
        final DittoHeaders dittoHeaders = createPolicy.getDittoHeaders();
        return CreatePolicy.of(policyWithNamespacedId, dittoHeaders);
    }

}