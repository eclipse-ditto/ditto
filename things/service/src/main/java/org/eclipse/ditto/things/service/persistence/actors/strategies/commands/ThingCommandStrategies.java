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

import org.apache.pekko.actor.ActorSystem;
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

    @SuppressWarnings("java:S3077") // volatile because of double checked locking pattern
    @Nullable
    private static volatile ThingCommandStrategies instance;

    /**
     * Constructs a new {@code ThingCommandReceiveStrategy} object.
     */
    private ThingCommandStrategies(final ActorSystem system) {
        super(Command.class);
        addThingStrategies(system);
        addPolicyStrategies(system);
        addAttributesStrategies(system);
        addDefinitionStrategies(system);
        addFeaturesStrategies(system);
        addFeatureDefinitionStrategies(system);
        addFeaturePropertiesStrategies(system);
        addFeatureDesiredPropertiesStrategies(system);
        addSudoStrategies(system);
    }

    /**
     * Returns the <em>singleton</em> instance of {@code ThingReceiveStrategy}.
     *
     * @param system the Pekko ActorSystem to use in order to e.g. dynamically load classes.
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
        addStrategy(new ThingConflictStrategy(system));
        addStrategy(new ModifyThingStrategy(system));
        addStrategy(new RetrieveThingStrategy(system));
        addStrategy(new DeleteThingStrategy(system));
        addStrategy(new MergeThingStrategy(system));
    }

    private void addPolicyStrategies(final ActorSystem system) {
        addStrategy(new RetrievePolicyIdStrategy(system));
        addStrategy(new ModifyPolicyIdStrategy(system));
    }

    private void addAttributesStrategies(final ActorSystem system) {
        addStrategy(new ModifyAttributesStrategy(system));
        addStrategy(new ModifyAttributeStrategy(system));
        addStrategy(new RetrieveAttributesStrategy(system));
        addStrategy(new RetrieveAttributeStrategy(system));
        addStrategy(new DeleteAttributesStrategy(system));
        addStrategy(new DeleteAttributeStrategy(system));
    }

    private void addDefinitionStrategies(final ActorSystem system) {
        addStrategy(new ModifyThingDefinitionStrategy(system));
        addStrategy(new RetrieveThingDefinitionStrategy(system));
        addStrategy(new DeleteThingDefinitionStrategy(system));
    }

    private void addFeaturesStrategies(final ActorSystem system) {
        addStrategy(new ModifyFeaturesStrategy(system));
        addStrategy(new ModifyFeatureStrategy(system));
        addStrategy(new RetrieveFeaturesStrategy(system));
        addStrategy(new RetrieveFeatureStrategy(system));
        addStrategy(new DeleteFeaturesStrategy(system));
        addStrategy(new DeleteFeatureStrategy(system));
    }

    private void addFeatureDefinitionStrategies(final ActorSystem system) {
        addStrategy(new ModifyFeatureDefinitionStrategy(system));
        addStrategy(new RetrieveFeatureDefinitionStrategy(system));
        addStrategy(new DeleteFeatureDefinitionStrategy(system));
    }

    private void addFeaturePropertiesStrategies(final ActorSystem system) {
        addStrategy(new ModifyFeaturePropertiesStrategy(system));
        addStrategy(new ModifyFeaturePropertyStrategy(system));
        addStrategy(new RetrieveFeaturePropertiesStrategy(system));
        addStrategy(new RetrieveFeaturePropertyStrategy(system));
        addStrategy(new DeleteFeaturePropertiesStrategy(system));
        addStrategy(new DeleteFeaturePropertyStrategy(system));
    }

    private void addFeatureDesiredPropertiesStrategies(final ActorSystem system) {
        addStrategy(new ModifyFeatureDesiredPropertiesStrategy(system));
        addStrategy(new ModifyFeatureDesiredPropertyStrategy(system));
        addStrategy(new RetrieveFeatureDesiredPropertiesStrategy(system));
        addStrategy(new RetrieveFeatureDesiredPropertyStrategy(system));
        addStrategy(new DeleteFeatureDesiredPropertiesStrategy(system));
        addStrategy(new DeleteFeatureDesiredPropertyStrategy(system));
    }

    private void addSudoStrategies(final ActorSystem system) {
        addStrategy(new SudoRetrieveThingStrategy(system));
    }

    @Override
    protected Result<ThingEvent<?>> getEmptyResult() {
        return ResultFactory.emptyResult();
    }

}
