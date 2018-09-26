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
package org.eclipse.ditto.services.things.persistence.actors.strategies.events;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.events.things.AclEntryCreated;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;
import org.eclipse.ditto.signals.events.things.AclEntryModified;
import org.eclipse.ditto.signals.events.things.AclModified;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributeDeleted;
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.eclipse.ditto.signals.events.things.AttributesCreated;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;
import org.eclipse.ditto.signals.events.things.AttributesModified;
import org.eclipse.ditto.signals.events.things.FeatureCreated;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionCreated;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionDeleted;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionModified;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;
import org.eclipse.ditto.signals.events.things.FeatureModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertyCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertyModified;
import org.eclipse.ditto.signals.events.things.FeaturesCreated;
import org.eclipse.ditto.signals.events.things.FeaturesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturesModified;
import org.eclipse.ditto.signals.events.things.PolicyIdCreated;
import org.eclipse.ditto.signals.events.things.PolicyIdModified;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This Singleton strategy handles all {@link org.eclipse.ditto.signals.events.things.ThingEvent}s.
 */
@Immutable
public final class EventHandleStrategy implements EventStrategy<ThingEvent> {

    private static final EventHandleStrategy INSTANCE = new EventHandleStrategy();

    private static final Logger LOGGER = LoggerFactory.getLogger(EventHandleStrategy.class);

    private final Map<Class<? extends ThingEvent>, EventStrategy<? extends ThingEvent>> strategies;

    /**
     * Returns the <em>singleton</em> {@code EventHandleStrategy} instance.
     *
     * @return the instance.
     */
    public static EventHandleStrategy getInstance() {
        return INSTANCE;
    }

    /**
     * Constructs a new {@code EventHandleStrategy}.
     */
    private EventHandleStrategy() {
        strategies = new HashMap<>();
        addThingStrategies();
        addAclStrategies();
        addAttributesStrategies();
        addFeaturesStrategies();
        addPolicyIdStrategies();
    }

    private void addThingStrategies() {
        addStrategy(ThingCreated.class, new ThingCreatedStrategy());
        addStrategy(ThingModified.class, new ThingModifiedStrategy());
        addStrategy(ThingDeleted.class, new ThingDeletedStrategy());
    }

    private void addAclStrategies() {
        addStrategy(AclModified.class, new AclModifiedStrategy());
        addStrategy(AclEntryCreated.class, new AclEntryCreatedStrategy());
        addStrategy(AclEntryModified.class, new AclEntryModifiedStrategy());
        addStrategy(AclEntryDeleted.class, new AclEntryDeletedStrategy());
    }

    private void addAttributesStrategies() {
        addStrategy(AttributesCreated.class, new AttributesCreatedStrategy());
        addStrategy(AttributesModified.class, new AttributesModifiedStrategy());
        addStrategy(AttributesDeleted.class, new AttributesDeletedStrategy());

        addStrategy(AttributeCreated.class, new AttributeCreatedStrategy());
        addStrategy(AttributeModified.class, new AttributeModifiedStrategy());
        addStrategy(AttributeDeleted.class, new AttributeDeletedStrategy());
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
    }

    private void addPolicyIdStrategies() {
        addStrategy(PolicyIdCreated.class, new PolicyIdCreatedStrategy());
        addStrategy(PolicyIdModified.class, new PolicyIdModifiedStrategy());
    }

    private <T extends ThingEvent> void addStrategy(final Class<T> cls, final EventStrategy<T> strategy) {
        strategies.put(cls, strategy);
    }

    @Override
    public Thing handle(final ThingEvent event, final Thing thing, final long revision) {
        checkNotNull(event, "ThingEvent");
        @SuppressWarnings("unchecked")
        final EventStrategy<ThingEvent> strategy = (EventStrategy<ThingEvent>) strategies.get(event.getClass());
        if (null != strategy) {
            return strategy.handle(event, thing, revision);
        } else {
            LOGGER.info("No strategy found for event <{}>.", event.getType());
            return thing;
        }
    }

}
