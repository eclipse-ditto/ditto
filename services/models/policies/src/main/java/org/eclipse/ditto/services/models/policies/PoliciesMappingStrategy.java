/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.models.policies;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoCommandRegistry;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoCommandResponseRegistry;
import org.eclipse.ditto.services.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.services.models.streaming.StreamingRegistry;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;
import org.eclipse.ditto.services.utils.distributedcache.model.BaseCacheEntry;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandRegistry;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandRegistry;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyErrorRegistry;
import org.eclipse.ditto.signals.events.policies.PolicyEventRegistry;

/**
 * {@link MappingStrategy} for the Policies service containing all {@link Jsonifiable} types known to Policies.
 */
public final class PoliciesMappingStrategy implements MappingStrategy {

    @Override
    public Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> determineStrategy() {
        final MappingStrategiesBuilder builder = MappingStrategiesBuilder.newInstance();

        addPoliciesStrategies(builder);
        addDevOpsStrategies(builder);

        return builder.build();
    }

    private static void addPoliciesStrategies(final MappingStrategiesBuilder builder) {
        builder
                .add(PolicyErrorRegistry.newInstance())
                .add(PolicyCommandRegistry.newInstance())
                .add(PolicyCommandResponseRegistry.newInstance())
                .add(PolicyEventRegistry.newInstance())
                .add(SudoCommandRegistry.newInstance())
                .add(SudoCommandResponseRegistry.newInstance())
                .add(StreamingRegistry.newInstance())
                .add(Policy.class, (Function<JsonObject, Jsonifiable<?>>) PoliciesModelFactory::newPolicy)
                .add(BaseCacheEntry.class,
                        jsonObject -> BaseCacheEntry.fromJson(jsonObject)) // do not replace with lambda!
                .add(PolicyCacheEntry.class,
                        jsonObject -> PolicyCacheEntry.fromJson(jsonObject)) // do not replace with lambda!
                .add(PolicyTag.class, jsonObject -> PolicyTag.fromJson(jsonObject))  // do not replace with lambda!
                .add(BatchedEntityIdWithRevisions.typeOf(PolicyTag.class),
                        BatchedEntityIdWithRevisions.deserializer(jsonObject -> PolicyTag.fromJson(jsonObject)))
                .add(PolicyReferenceTag.class, jsonObject -> PolicyReferenceTag.fromJson(jsonObject));  // do not replace with lambda!
    }

    private static void addDevOpsStrategies(final MappingStrategiesBuilder builder) {
        builder.add(DevOpsCommandRegistry.newInstance());
        builder.add(DevOpsCommandResponseRegistry.newInstance());
    }
}
