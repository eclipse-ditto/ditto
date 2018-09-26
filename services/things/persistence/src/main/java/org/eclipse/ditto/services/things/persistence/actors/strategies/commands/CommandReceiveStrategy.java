/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;

import akka.event.DiagnosticLoggingAdapter;

/**
 * This <em>Singleton</em> delegates a {@code Command} to a dedicated strategy - if one is available - to be handled.
 */
@Immutable
public final class CommandReceiveStrategy extends AbstractCommandStrategy<Command> {

    private static final CommandReceiveStrategy INSTANCE = new CommandReceiveStrategy();

    private final Map<Class<? extends Command>, CommandStrategy<? extends Command>> strategies;

    /**
     * Constructs a new {@code ThingCommandReceiveStrategy} object.
     */
    private CommandReceiveStrategy() {
        super(Command.class);
        strategies = new HashMap<>();
        addThingStrategies();
        addPolicyStrategies();
        addAclStrategies();
        addAttributesStrategies();
        addFeaturesStrategies();
        addFeatureDefinitionStrategies();
        addFeatureStrategies();
        addSudoStrategies();
    }

    private void addThingStrategies() {
        addStrategy(new ThingConflictStrategy());
        addStrategy(new ModifyThingStrategy());
        addStrategy(new RetrieveThingStrategy());
        addStrategy(new DeleteThingStrategy());
    }

    private void addPolicyStrategies() {
        addStrategy(new RetrievePolicyIdStrategy());
        addStrategy(new ModifyPolicyIdStrategy());
    }

    private void addAclStrategies() {
        addStrategy(new ModifyAclStrategy());
        addStrategy(new RetrieveAclStrategy());
        addStrategy(new ModifyAclEntryStrategy());
        addStrategy(new RetrieveAclEntryStrategy());
        addStrategy(new DeleteAclEntryStrategy());
    }

    private void addAttributesStrategies() {
        addStrategy(new ModifyAttributesStrategy());
        addStrategy(new ModifyAttributeStrategy());
        addStrategy(new RetrieveAttributesStrategy());
        addStrategy(new RetrieveAttributeStrategy());
        addStrategy(new DeleteAttributesStrategy());
        addStrategy(new DeleteAttributeStrategy());
    }

    private void addFeaturesStrategies() {
        addStrategy(new ModifyFeaturesStrategy());
        addStrategy(new ModifyFeatureStrategy());
        addStrategy(new RetrieveFeaturesStrategy());
        addStrategy(new RetrieveFeatureStrategy());
        addStrategy(new DeleteFeaturesStrategy());
        addStrategy(new DeleteFeatureStrategy());
    }

    private void addFeatureDefinitionStrategies() {
        addStrategy(new ModifyFeatureDefinitionStrategy());
        addStrategy(new RetrieveFeatureDefinitionStrategy());
        addStrategy(new DeleteFeatureDefinitionStrategy());
    }

    private void addFeatureStrategies() {
        addStrategy(new ModifyFeaturePropertiesStrategy());
        addStrategy(new ModifyFeaturePropertyStrategy());
        addStrategy(new RetrieveFeaturePropertiesStrategy());
        addStrategy(new RetrieveFeaturePropertyStrategy());
        addStrategy(new DeleteFeaturePropertiesStrategy());
        addStrategy(new DeleteFeaturePropertyStrategy());
    }

    private void addSudoStrategies() {
        addStrategy(new SudoRetrieveThingStrategy());
    }

    private void addStrategy(final CommandStrategy<? extends Command> strategy) {
        final Class<? extends Command> matchingClass = strategy.getMatchingClass();
        strategies.put(matchingClass, strategy);
    }

    /**
     * Returns the <em>singleton</em> instance of {@code CommandReceiveStrategy}.
     *
     * @return the instance.
     */
    public static CommandReceiveStrategy getInstance() {
        return INSTANCE;
    }

    @Override
    protected Result unhandled(final Context context, final Thing thing,
            final long nextRevision, final Command command) {
        final DiagnosticLoggingAdapter log = context.getLog();
        LogUtil.enhanceLogWithCorrelationId(log, command);
        log.info("Command of type <{}> cannot be handled by this strategy.", command.getClass().getName());

        return ResultFactory.emptyResult();
    }

    @Override
    public boolean isDefined(final Command command) {
        return strategies.containsKey(command.getClass());
    }

    @Override
    public boolean isDefined(final Context context, final Thing thing,
            final Command command) {
        return isDefined(command);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final Command command) {
        final CommandStrategy<Command> commandStrategy = getAppropriateStrategy(command.getClass());

        final DiagnosticLoggingAdapter log = context.getLog();
        LogUtil.enhanceLogWithCorrelationId(log, command);
        if (null != commandStrategy) {
            LogUtil.enhanceLogWithCorrelationId(log, command.getDittoHeaders().getCorrelationId());
            if (log.isDebugEnabled()) {
                log.debug("Applying command <{}>: {}", command.getType(), command.toJsonString());
            }
            return commandStrategy.apply(context, thing, nextRevision, command);
        }

        log.info("No strategy found for command <{}>.", command.getType());
        return Result.empty();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private CommandStrategy<Command> getAppropriateStrategy(final Class commandClass) {
        return (CommandStrategy<Command>) strategies.get(commandClass);
    }

}
