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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;

public class CommandReceiveStrategy extends AbstractCommandStrategy<Command> {

    private final Map<Class<? extends Command>, CommandStrategy<? extends Command>> strategies = new HashMap<>();


    private static class LazyHolder {

        static final CommandReceiveStrategy INSTANCE = new CommandReceiveStrategy();

    }

    public static CommandReceiveStrategy getInstance() {
        return LazyHolder.INSTANCE;
    }

    /**
     * Constructs a new {@code ThingCommandReceiveStrategy} object.
     */
    private CommandReceiveStrategy() {
        super(Command.class);
        addThingStrategies();
        addPolicyStrategies();
        addAclStrategies();
        addAttributesStrategies();
        addFeaturesStrategies();
        addFeatureDefinitionStrategies();
        addFeatureStrategies();
        addSudoStrategies();
    }

    private void addSudoStrategies() {
        addStrategy(new SudoRetrieveThingStrategy());
    }

    private void addFeatureStrategies() {
        addStrategy(new ModifyFeaturePropertiesStrategy());
        addStrategy(new ModifyFeaturePropertyStrategy());
        addStrategy(new RetrieveFeaturePropertiesStrategy());
        addStrategy(new RetrieveFeaturePropertyStrategy());
        addStrategy(new DeleteFeaturePropertiesStrategy());
        addStrategy(new DeleteFeaturePropertyStrategy());
    }

    private void addFeatureDefinitionStrategies() {
        addStrategy(new ModifyFeatureDefinitionStrategy());
        addStrategy(new RetrieveFeatureDefinitionStrategy());
        addStrategy(new DeleteFeatureDefinitionStrategy());
    }

    private void addFeaturesStrategies() {
        addStrategy(new ModifyFeaturesStrategy());
        addStrategy(new ModifyFeatureStrategy());
        addStrategy(new RetrieveFeaturesStrategy());
        addStrategy(new RetrieveFeatureStrategy());
        addStrategy(new DeleteFeaturesStrategy());
        addStrategy(new DeleteFeatureStrategy());
    }

    private void addAttributesStrategies() {
        addStrategy(new ModifyAttributesStrategy());
        addStrategy(new ModifyAttributeStrategy());
        addStrategy(new RetrieveAttributesStrategy());
        addStrategy(new RetrieveAttributeStrategy());
        addStrategy(new DeleteAttributesStrategy());
        addStrategy(new DeleteAttributeStrategy());
    }

    private void addAclStrategies() {
        addStrategy(new ModifyAclStrategy());
        addStrategy(new RetrieveAclStrategy());
        addStrategy(new ModifyAclEntryStrategy());
        addStrategy(new RetrieveAclEntryStrategy());
        addStrategy(new DeleteAclEntryStrategy());
    }

    private void addPolicyStrategies() {
        addStrategy(new RetrievePolicyIdStrategy());
        addStrategy(new ModifyPolicyIdStrategy());
    }

    private void addThingStrategies() {
        addStrategy(new ThingConflictStrategy());
        addStrategy(new ModifyThingStrategy());
        addStrategy(new RetrieveThingStrategy());
        addStrategy(new DeleteThingStrategy());
    }

    private void addStrategy(final CommandStrategy<? extends Command> strategy) {
        final Class<? extends Command> matchingClass = strategy.getMatchingClass();
        strategies.put(matchingClass, strategy);
    }

    @Override
    protected Result unhandled(final Context context, final Command command) {
        context.getLog().info("Command of type '{}' cannot be handled by this strategy.", command.getClass().getName());
        return ImmutableResult.empty();
    }

    @Override
    public boolean isDefined(final Context context, final Command command) {
        return strategies.containsKey(command.getClass());
    }

    @Override
    protected Result doApply(final Context context, final Command command) {
        final CommandStrategy<Command> commandStrategy = (CommandStrategy<Command>) strategies.get(command.getClass());
        if (commandStrategy != null) {
            LogUtil.enhanceLogWithCorrelationId(context.getLog(), command.getDittoHeaders().getCorrelationId());
            if (context.getLog().isDebugEnabled()) {
                context.getLog().debug("Applying command '{}': {}", command.getType(), command.toJsonString());
            }
            return commandStrategy.apply(context, command);
        } else {
            context.getLog().info("No strategy found for command {}.", command.getType());
            return Result.empty();
        }
    }

}
