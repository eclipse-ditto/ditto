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
package org.eclipse.ditto.things.service.persistence.actors.strategies.events;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistentactors.events.AbstractEventStrategies;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.events.AttributeCreated;
import org.eclipse.ditto.things.model.signals.events.AttributeDeleted;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.things.model.signals.events.AttributesCreated;
import org.eclipse.ditto.things.model.signals.events.AttributesDeleted;
import org.eclipse.ditto.things.model.signals.events.AttributesModified;
import org.eclipse.ditto.things.model.signals.events.FeatureCreated;
import org.eclipse.ditto.things.model.signals.events.FeatureDefinitionCreated;
import org.eclipse.ditto.things.model.signals.events.FeatureDefinitionDeleted;
import org.eclipse.ditto.things.model.signals.events.FeatureDefinitionModified;
import org.eclipse.ditto.things.model.signals.events.FeatureDeleted;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertiesCreated;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertiesDeleted;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertiesModified;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertyCreated;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertyDeleted;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertyModified;
import org.eclipse.ditto.things.model.signals.events.FeatureModified;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertiesCreated;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertiesDeleted;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertiesModified;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyCreated;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyDeleted;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyModified;
import org.eclipse.ditto.things.model.signals.events.FeaturesCreated;
import org.eclipse.ditto.things.model.signals.events.FeaturesDeleted;
import org.eclipse.ditto.things.model.signals.events.FeaturesModified;
import org.eclipse.ditto.things.model.signals.events.PolicyIdModified;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionCreated;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionModified;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.eclipse.ditto.things.model.signals.events.ThingModified;

/**
 * This Singleton strategy handles all {@link ThingEvent}s.
 */
@Immutable
public final class ThingEventStrategies extends AbstractEventStrategies<ThingEvent<?>, Thing> {

    private static final ThingEventStrategies INSTANCE = new ThingEventStrategies();

    /**
     * Returns the <em>singleton</em> {@code EventHandleStrategy} instance.
     *
     * @return the instance.
     */
    public static ThingEventStrategies getInstance() {
        return INSTANCE;
    }

    /**
     * Constructs a new {@code ThingEventHandleStrategy}.
     */
    private ThingEventStrategies() {
        addThingStrategies();
        addAttributesStrategies();
        addDefinitionStrategies();
        addFeaturesStrategies();
        addPolicyIdStrategies();
    }

    private void addThingStrategies() {
        addStrategy(ThingCreated.class, new ThingCreatedStrategy());
        addStrategy(ThingModified.class, new ThingModifiedStrategy());
        addStrategy(ThingDeleted.class, new ThingDeletedStrategy());
        addStrategy(ThingMerged.class, new ThingMergedStrategy());
    }

    private void addAttributesStrategies() {
        addStrategy(AttributesCreated.class, new AttributesCreatedStrategy());
        addStrategy(AttributesModified.class, new AttributesModifiedStrategy());
        addStrategy(AttributesDeleted.class, new AttributesDeletedStrategy());

        addStrategy(AttributeCreated.class, new AttributeCreatedStrategy());
        addStrategy(AttributeModified.class, new AttributeModifiedStrategy());
        addStrategy(AttributeDeleted.class, new AttributeDeletedStrategy());
    }

    private void addDefinitionStrategies() {
        addStrategy(ThingDefinitionCreated.class, new ThingDefinitionCreatedStrategy());
        addStrategy(ThingDefinitionModified.class, new ThingDefinitionModifiedStrategy());
        addStrategy(ThingDefinitionDeleted.class, new ThingDefinitionDeletedStrategy());
    }

    private void addFeaturesStrategies() {
        addStrategy(FeaturesCreated.class, new FeaturesCreatedStrategy());
        addStrategy(FeaturesModified.class, new FeaturesModifiedStrategy());
        addStrategy(FeaturesDeleted.class, new FeaturesDeletedStrategy());

        addStrategy(FeatureCreated.class, new FeatureCreatedStrategy());
        addStrategy(FeatureModified.class, new FeatureModifiedStrategy());
        addStrategy(FeatureDeleted.class, new FeatureDeletedStrategy());

        addStrategy(FeatureDefinitionCreated.class, new FeatureDefinitionCreatedStrategy());
        addStrategy(FeatureDefinitionModified.class, new FeatureDefinitionModifiedStrategy());
        addStrategy(FeatureDefinitionDeleted.class, new FeatureDefinitionDeletedStrategy());

        addStrategy(FeaturePropertiesCreated.class, new FeaturePropertiesCreatedStrategy());
        addStrategy(FeaturePropertiesModified.class, new FeaturePropertiesModifiedStrategy());
        addStrategy(FeaturePropertiesDeleted.class, new FeaturePropertiesDeletedStrategy());

        addStrategy(FeaturePropertyCreated.class, new FeaturePropertyCreatedStrategy());
        addStrategy(FeaturePropertyModified.class, new FeaturePropertyModifiedStrategy());
        addStrategy(FeaturePropertyDeleted.class, new FeaturePropertyDeletedStrategy());

        addStrategy(FeatureDesiredPropertiesCreated.class, new FeatureDesiredPropertiesCreatedStrategy());
        addStrategy(FeatureDesiredPropertiesModified.class, new FeatureDesiredPropertiesModifiedStrategy());
        addStrategy(FeatureDesiredPropertiesDeleted.class, new FeatureDesiredPropertiesDeletedStrategy());

        addStrategy(FeatureDesiredPropertyCreated.class, new FeatureDesiredPropertyCreatedStrategy());
        addStrategy(FeatureDesiredPropertyModified.class, new FeatureDesiredPropertyModifiedStrategy());
        addStrategy(FeatureDesiredPropertyDeleted.class, new FeatureDesiredPropertyDeletedStrategy());
    }

    private void addPolicyIdStrategies() {
        addStrategy(PolicyIdModified.class, new PolicyIdModifiedStrategy());
    }
}
