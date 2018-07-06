/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.util.Map;

import org.eclipse.ditto.signals.commands.base.Command;

public class CommandReceiveStrategy {

    private Map<Class<? extends Command>, ReceiveStrategy<? extends Command>> strategies;

    /**
     * Constructs a new {@code ThingCommandReceiveStrategy} object.
     */
    public CommandReceiveStrategy() {

        addStrategy(new ThingConflictStrategy());

        addStrategy(new ModifyThingStrategy());
        addStrategy(new RetrieveThingStrategy());
        addStrategy(new DeleteThingStrategy());

        // Policy ID
        addStrategy(new RetrievePolicyIdStrategy());
        addStrategy(new ModifyPolicyIdStrategy());

        // ACL
        addStrategy(new ModifyAclStrategy());
        addStrategy(new RetrieveAclStrategy());
        addStrategy(new ModifyAclEntryStrategy());
        addStrategy(new RetrieveAclEntryStrategy());
        addStrategy(new DeleteAclEntryStrategy());

        // Attributes
        addStrategy(new ModifyAttributesStrategy());
        addStrategy(new ModifyAttributeStrategy());
//        addStrategy(new RetrieveAttributesStrategy());
//        addStrategy(new RetrieveAttributeStrategy());
        addStrategy(new DeleteAttributesStrategy());
        addStrategy(new DeleteAttributeStrategy());

        // Features
//        addStrategy(new ModifyFeaturesStrategy());
//        addStrategy(new ModifyFeatureStrategy());
//        addStrategy(new RetrieveFeaturesStrategy());
//        addStrategy(new RetrieveFeatureStrategy());
//        addStrategy(new DeleteFeaturesStrategy());
//        addStrategy(new DeleteFeatureStrategy());
//
//        // Feature Definition
//        addStrategy(new ModifyFeatureDefinitionStrategy());
//        addStrategy(new RetrieveFeatureDefinitionStrategy());
//        addStrategy(new DeleteFeatureDefinitionStrategy());
//
//        // Feature Properties
//        addStrategy(new ModifyFeaturePropertiesStrategy());
//        addStrategy(new ModifyFeaturePropertyStrategy());
//        addStrategy(new RetrieveFeaturePropertiesStrategy());
//        addStrategy(new RetrieveFeaturePropertyStrategy());
//        addStrategy(new DeleteFeaturePropertiesStrategy());
//        addStrategy(new DeleteFeaturePropertyStrategy());

        // sudo
        addStrategy(new SudoRetrieveThingStrategy());

        // Persistence specific
//        addStrategy(new CheckForActivityStrategy());

    }

    private void addStrategy(ReceiveStrategy<? extends Command> strategy) {
        final Class<? extends Command> matchingClass = strategy.getMatchingClass();
        strategies.put(matchingClass, strategy);
    }

    public boolean isDefinedAt(Class<?> cls) {
        return strategies.containsKey(cls);
    }

    public <T extends Command<?>> ReceiveStrategy.Result handle(final ReceiveStrategy.Context context,
            final T message) {
        final Class<? extends Command> aClass = message.getClass();
        final ReceiveStrategy<? extends Command> receiveStrategy = strategies.get(aClass);
        if (receiveStrategy != null) {
            if (receiveStrategy.getPredicate().apply(context, message)) {
                return receiveStrategy.getApplyFunction().apply(context, message);
            } else {
                return receiveStrategy.getUnhandledFunction().apply(context, message);
            }

        } else {
            context.log().info("Command of type '{}' cannot be handled by this strategy.", aClass.getName());
            return ImmutableResult.empty();
        }
    }
}
