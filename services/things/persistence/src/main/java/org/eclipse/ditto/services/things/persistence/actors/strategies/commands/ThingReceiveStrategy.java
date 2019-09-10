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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.commands.AbstractReceiveStrategy;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * The collection of the command strategies of {@code ThingPersistenceActor}.
 */
public final class ThingReceiveStrategy extends AbstractReceiveStrategy<Command, Thing, ThingId, Result<ThingEvent>> {

    private static final ThingReceiveStrategy INSTANCE = new ThingReceiveStrategy();

    /**
     * Constructs a new {@code ThingCommandReceiveStrategy} object.
     */
    private ThingReceiveStrategy() {
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

    /**
     * Returns the <em>singleton</em> instance of {@code CommandReceiveStrategy}.
     *
     * @return the instance.
     */
    public static ThingReceiveStrategy getInstance() {
        return INSTANCE;
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

    @Override
    protected Result<ThingEvent> getEmptyResult() {
        return ResultFactory.emptyResult();
    }
}
