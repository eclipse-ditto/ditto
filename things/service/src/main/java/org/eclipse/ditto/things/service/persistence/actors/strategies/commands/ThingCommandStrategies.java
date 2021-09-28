/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.persistentactors.commands.AbstractCommandStrategies;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * The collection of the command strategies of {@code ThingPersistenceActor}.
 */
public final class ThingCommandStrategies
        extends AbstractCommandStrategies<Command<?>, Thing, ThingId, ThingEvent<?>> {

    private static final ThingCommandStrategies INSTANCE = new ThingCommandStrategies();

    /**
     * Constructs a new {@code ThingCommandReceiveStrategy} object.
     */
    private ThingCommandStrategies() {
        super(Command.class);
        addThingStrategies();
        addPolicyStrategies();
        addAttributesStrategies();
        addDefinitionStrategies();
        addFeaturesStrategies();
        addFeatureDefinitionStrategies();
        addFeaturePropertiesStrategies();
        addFeatureDesiredPropertiesStrategies();
        addSudoStrategies();
    }

    /**
     * Returns the <em>singleton</em> instance of {@code ThingReceiveStrategy}.
     *
     * @return the instance.
     */
    public static ThingCommandStrategies getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the <em>singleton</em> instance of {@code CreateThingStrategy}.
     *
     * @return the instance.
     */
    public static CommandStrategy<CreateThing, Thing, ThingId, ThingEvent<?>> getCreateThingStrategy() {
        return CreateThingStrategy.getInstance();
    }

    private void addThingStrategies() {
        addStrategy(new ThingConflictStrategy());
        addStrategy(new ModifyThingStrategy());
        addStrategy(new RetrieveThingStrategy());
        addStrategy(new DeleteThingStrategy());
        addStrategy(new MergeThingStrategy());
    }

    private void addPolicyStrategies() {
        addStrategy(new RetrievePolicyIdStrategy());
        addStrategy(new ModifyPolicyIdStrategy());
    }

    private void addAttributesStrategies() {
        addStrategy(new ModifyAttributesStrategy());
        addStrategy(new ModifyAttributeStrategy());
        addStrategy(new RetrieveAttributesStrategy());
        addStrategy(new RetrieveAttributeStrategy());
        addStrategy(new DeleteAttributesStrategy());
        addStrategy(new DeleteAttributeStrategy());
    }

    private void addDefinitionStrategies() {
        addStrategy(new ModifyThingDefinitionStrategy());
        addStrategy(new RetrieveThingDefinitionStrategy());
        addStrategy(new DeleteThingDefinitionStrategy());
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

    private void addFeaturePropertiesStrategies() {
        addStrategy(new ModifyFeaturePropertiesStrategy());
        addStrategy(new ModifyFeaturePropertyStrategy());
        addStrategy(new RetrieveFeaturePropertiesStrategy());
        addStrategy(new RetrieveFeaturePropertyStrategy());
        addStrategy(new DeleteFeaturePropertiesStrategy());
        addStrategy(new DeleteFeaturePropertyStrategy());
    }

    private void addFeatureDesiredPropertiesStrategies() {
        addStrategy(new ModifyFeatureDesiredPropertiesStrategy());
        addStrategy(new ModifyFeatureDesiredPropertyStrategy());
        addStrategy(new RetrieveFeatureDesiredPropertiesStrategy());
        addStrategy(new RetrieveFeatureDesiredPropertyStrategy());
        addStrategy(new DeleteFeatureDesiredPropertiesStrategy());
        addStrategy(new DeleteFeatureDesiredPropertyStrategy());
    }

    private void addSudoStrategies() {
        addStrategy(new SudoRetrieveThingStrategy());
    }

    @Override
    protected Result<ThingEvent<?>> getEmptyResult() {
        return ResultFactory.emptyResult();
    }

}
