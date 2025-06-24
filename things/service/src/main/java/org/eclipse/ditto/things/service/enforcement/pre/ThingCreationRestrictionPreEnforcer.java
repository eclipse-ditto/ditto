/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.policies.enforcement.config.CreationRestrictionConfig;
import org.eclipse.ditto.policies.enforcement.pre.CreationRestrictionPreEnforcer;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;

import com.typesafe.config.Config;

/**
 * Pre-Enforcer for evaluating if creation of new Things should be restricted.
 * This class extends {@link CreationRestrictionPreEnforcer} to provide Thing-specific creation restrictions.
 */
public final class ThingCreationRestrictionPreEnforcer
        extends CreationRestrictionPreEnforcer<ThingCreationRestrictionPreEnforcer.ThingContext> {

    /**
     * Constructs a new instance of ThingCreationRestrictionPreEnforcer extension.
     *
     * @param actorSystem the actor system in which to load the extension.
     * @param config the configuration for this extension.
     */
    public ThingCreationRestrictionPreEnforcer(final ActorSystem actorSystem,
            final Config config) {
        super(actorSystem, config);
    }

    @Override
    protected ThingContext createContext(final Signal<?> signal, final NamespacedEntityId entityId) {
        if (signal instanceof CreateThing createThing) {
            // only commands are checked for creation restrictions
            return new ThingContext(signal.getResourceType(), entityId.getNamespace(),
                    createThing.getThing().getDefinition().orElse(null), signal.getDittoHeaders());
        } else {
            LOG.withCorrelationId(signal.getDittoHeaders())
                    .warn("Did not receive a CreateThing signal, but a <{}> signal instead. " +
                                    "Creation restrictions are only applied to CreateThing signals.",
                            signal.getClass().getSimpleName()
                    );
            return new ThingContext(signal.getResourceType(), entityId.getNamespace(), null, signal.getDittoHeaders());
        }
    }

    @Override
    protected boolean matchesResourceTypeSpecifics(final CreationRestrictionConfig creationRestrictionConfig,
            final ThingContext context) {

        final List<Pattern> thingDefinitionPatterns = creationRestrictionConfig.getThingDefinitions();
        if (thingDefinitionPatterns.isEmpty()) {
            LOG.withCorrelationId(context.headers()).debug("No thing definition restriction: pass");
            // no restrictions -> pass
            return true;
        }

        if (!context.resourceType().equals("thing")) {
            LOG.withCorrelationId(context.headers())
                    .debug("No thing resource to check ThingDefinition restriction: pass");
            // no thing resource -> pass
            return true;
        }

        var thingDefinitionStr = Optional.ofNullable(context.thingDefinition)
                .map(ThingDefinition::toString)
                .orElse(null);
        for (final var thingDefinitionPattern : thingDefinitionPatterns) {
            if (thingDefinitionStr == null) {
                if (thingDefinitionPattern == null) {
                    LOG.withCorrelationId(context.headers())
                            .debug("null ThingDefinition matched null allowed pattern: pass");
                    return true;
                }
            } else if (thingDefinitionPattern != null && thingDefinitionPattern.matcher(thingDefinitionStr).matches()) {
                    LOG.withCorrelationId(context.headers())
                            .debug("ThingDefinition '{}' matched {}: pass", thingDefinitionStr,
                                    thingDefinitionPattern);
                    return true;
                }

        }

        LOG.withCorrelationId(context.headers()).debug("No ThingDefinition match: reject");
        // no match, but non-empty list -> reject
        return false;
    }


    protected record ThingContext(String resourceType, String namespace, @Nullable ThingDefinition thingDefinition,
                                  DittoHeaders headers) implements Context {}
}
