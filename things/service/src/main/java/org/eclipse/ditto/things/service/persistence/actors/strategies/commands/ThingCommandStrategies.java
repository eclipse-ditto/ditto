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

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.persistentactors.commands.AbstractCommandStrategies;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import akka.actor.ActorSystem;

/**
 * The collection of the command strategies of {@code ThingPersistenceActor}.
 */
public final class ThingCommandStrategies
        extends AbstractCommandStrategies<Command<?>, Thing, ThingId, ThingEvent<?>> {

    @SuppressWarnings("java:S3077") // volatile because of double checked locking pattern
    @Nullable
    private static volatile ThingCommandStrategies instance;

    /**
     * Constructs a new {@code ThingCommandReceiveStrategy} object.
     */
    private ThingCommandStrategies(final ActorSystem system) {
        super(Command.class);
        addThingStrategies(system);
        addPolicyStrategies();
        addAttributesStrategies();
        addDefinitionStrategies();
        addFeaturesStrategies(system);
        addFeatureDefinitionStrategies();
        addFeaturePropertiesStrategies();
        addFeatureDesiredPropertiesStrategies();
        addSudoStrategies();
    }

    /**
     * Returns the <em>singleton</em> instance of {@code ThingReceiveStrategy}.
     *
     * @param system the Akka ActorSystem to use in order to e.g. dynamically load classes.
     * @return the instance.
     */
    public static ThingCommandStrategies getInstance(final ActorSystem system) {
        ThingCommandStrategies localInstance = instance;
        if (null == localInstance) {
            synchronized (ThingCommandStrategies.class) {
                localInstance = instance;
                if (null == localInstance) {
                    instance = localInstance = new ThingCommandStrategies(system);
                }
            }
        }
        return localInstance;
    }

    /**
     * Returns a new instance of {@code CreateThingStrategy}.
     *
     * @param system the ActorSystem to use.
     * @return the instance.
     */
    public static CommandStrategy<CreateThing, Thing, ThingId, ThingEvent<?>> getCreateThingStrategy(
            final ActorSystem system) {
        return new CreateThingStrategy(system);
    }

    private void addThingStrategies(final ActorSystem system) {
        addStrategy(new ThingConflictStrategy());
        addStrategy(new ModifyThingStrategy());
        addStrategy(new RetrieveThingStrategy(system));
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

    private void addFeaturesStrategies(final ActorSystem system) {
        addStrategy(new ModifyFeaturesStrategy(system));
        addStrategy(new ModifyFeatureStrategy(system));
        addStrategy(new RetrieveFeaturesStrategy());
        addStrategy(new RetrieveFeatureStrategy(system));
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
