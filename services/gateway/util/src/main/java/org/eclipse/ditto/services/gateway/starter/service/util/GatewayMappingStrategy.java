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
package org.eclipse.ditto.services.gateway.starter.service.util;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.policies.PolicyCacheEntry;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.models.things.ThingCacheEntry;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoCommandRegistry;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoCommandResponseRegistry;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.ThingSearchSudoCommandRegistry;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.ThingSearchSudoCommandResponseRegistry;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;
import org.eclipse.ditto.services.utils.distributedcache.model.BaseCacheEntry;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandRegistry;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.messages.MessageCommandRegistry;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.messages.MessageErrorRegistry;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandRegistry;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyErrorRegistry;
import org.eclipse.ditto.signals.commands.things.ThingCommandRegistry;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingErrorRegistry;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommandRegistry;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.ThingSearchErrorRegistry;
import org.eclipse.ditto.signals.events.policies.PolicyEventRegistry;
import org.eclipse.ditto.signals.events.things.ThingEventRegistry;

/**
 * {@link MappingStrategy} for the Gateway service containing all {@link Jsonifiable} types known to this service.
 */
public final class GatewayMappingStrategy implements MappingStrategy {

    @Override
    public Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> determineStrategy() {
        final MappingStrategiesBuilder builder = MappingStrategiesBuilder.newInstance();

        builder.add(BaseCacheEntry.class,
                jsonObject -> BaseCacheEntry.fromJson(jsonObject)); // do not replace with lambda!

        addPoliciesStrategies(builder);
        addThingsStrategies(builder);
        addMessagesStrategies(builder);
        addThingSearchStrategies(builder);
        addDevOpsStrategies(builder);

        return builder.build();
    }

    private void addPoliciesStrategies(final MappingStrategiesBuilder builder) {
        builder.add(PolicyErrorRegistry.newInstance())
                .add(PolicyCommandRegistry.newInstance())
                .add(PolicyCommandResponseRegistry.newInstance())
                .add(PolicyEventRegistry.newInstance())
                .add(org.eclipse.ditto.services.models.policies.commands.sudo.SudoCommandRegistry.newInstance())
                .add(org.eclipse.ditto.services.models.policies.commands.sudo.SudoCommandResponseRegistry.newInstance())
                .add(Policy.class, (Function<JsonObject, Jsonifiable<?>>) PoliciesModelFactory::newPolicy)
                .add(PolicyCacheEntry.class,
                        jsonObject -> PolicyCacheEntry.fromJson(jsonObject)) // do not replace with lambda!
                .add(PolicyTag.class, jsonObject -> PolicyTag.fromJson(jsonObject)) // do not replace with lambda!
        ;
    }

    private void addThingsStrategies(final MappingStrategiesBuilder builder) {
        builder.add(ThingErrorRegistry.newInstance())
                .add(ThingCommandRegistry.newInstance())
                .add(ThingCommandResponseRegistry.newInstance())
                .add(ThingEventRegistry.newInstance())
                .add(SudoCommandRegistry.newInstance())
                .add(SudoCommandResponseRegistry.newInstance())
                .add(Thing.class,
                        (jsonObject) -> ThingsModelFactory.newThing(jsonObject)) // do not replace with lambda!
                .add(ThingCacheEntry.class,
                        jsonObject -> ThingCacheEntry.fromJson(jsonObject)) // do not replace with lambda!
                .add(ThingTag.class, jsonObject -> ThingTag.fromJson(jsonObject)) // do not replace with lambda!
        ;
    }

    private static void addMessagesStrategies(final MappingStrategiesBuilder builder) {
        builder.add(MessageCommandRegistry.newInstance());
        builder.add(MessageCommandResponseRegistry.newInstance());
        builder.add(MessageErrorRegistry.newInstance());
    }

    private static void addThingSearchStrategies(final MappingStrategiesBuilder builder) {
        builder.add(ThingSearchCommandRegistry.newInstance());
        builder.add(ThingSearchCommandResponseRegistry.newInstance());
        builder.add(ThingSearchErrorRegistry.newInstance());
        builder.add(ThingSearchSudoCommandRegistry.newInstance());
        builder.add(ThingSearchSudoCommandResponseRegistry.newInstance());
    }

    private static void addDevOpsStrategies(final MappingStrategiesBuilder builder) {
        builder.add(DevOpsCommandRegistry.newInstance())
                .add(DevOpsCommandResponseRegistry.newInstance());
    }
}
