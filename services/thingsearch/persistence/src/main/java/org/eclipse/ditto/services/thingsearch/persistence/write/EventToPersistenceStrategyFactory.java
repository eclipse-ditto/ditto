/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.persistence.write;

import static java.util.Objects.requireNonNull;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * Factory class that creates {@link EventToPersistenceStrategy} instances for a given {@link ThingEvent}.
 *
 * @param <D> Type of the Updates for the Thing.
 * @param <P> Type of the Updates for the Policies of a Thing.
 */
public abstract class EventToPersistenceStrategyFactory<D, P> {

    private static final Set<String> AVAILABLE_TYPES;

    static {
        final Set<String> types = new HashSet<>();
        types.add(AclEntryCreated.TYPE);
        types.add(AclEntryModified.TYPE);
        types.add(AclEntryDeleted.TYPE);
        types.add(AclModified.TYPE);
        types.add(AttributeCreated.TYPE);
        types.add(AttributeModified.TYPE);
        types.add(AttributeDeleted.TYPE);
        types.add(AttributesCreated.TYPE);
        types.add(AttributesModified.TYPE);
        types.add(AttributesDeleted.TYPE);
        types.add(FeatureCreated.TYPE);
        types.add(FeatureModified.TYPE);
        types.add(FeatureDeleted.TYPE);
        types.add(FeaturesCreated.TYPE);
        types.add(FeaturesModified.TYPE);
        types.add(FeaturesDeleted.TYPE);
        types.add(FeaturePropertyCreated.TYPE);
        types.add(FeaturePropertyModified.TYPE);
        types.add(FeaturePropertyDeleted.TYPE);
        types.add(FeaturePropertiesCreated.TYPE);
        types.add(FeaturePropertiesModified.TYPE);
        types.add(FeaturePropertiesDeleted.TYPE);
        types.add(ThingDeleted.TYPE);
        AVAILABLE_TYPES = Collections.unmodifiableSet(types);
    }

    /**
     * Get the {@link EventToPersistenceStrategy} that allows to create persistable objects for the given {@code
     * event}.
     * This method will throw an {@link IllegalStateException} if the event type is not allowed or unknown.
     *
     * @param event The event.
     * @param <T> Type of the event.
     * @return The strategy to create persistable objects.
     */
    public final <T extends ThingEvent> EventToPersistenceStrategy<T, D, P> getStrategy(final T event) {
        requireNonNull(event);
        return getStrategyForType(event.getType());
    }

    private <T extends ThingEvent> EventToPersistenceStrategy<T, D, P> getStrategyForType(final String type) {
        if (isTypeAllowed(type)) {
            return getInstance(type);
        }
        final String pattern = "Event type <{0}> is unknown!";
        throw new IllegalStateException(MessageFormat.format(pattern, type));
    }

    /**
     * Get an instance of the {@link EventToPersistenceStrategy} that creates persistable objects.
     *
     * @param type The type of the {@link ThingEvent} using {@link ThingEvent#getType()}.
     * @param <T> Type of the event.
     * @return The strategy to create persistable objects or null if none could be found.
     */
    protected abstract <T extends ThingEvent>  EventToPersistenceStrategy<T, D, P> getInstance(final String type);

    /**
     * Checks if the given {@code type} is allowed for creating {@link EventToPersistenceStrategy}.
     *
     * @param type The type of the {@link ThingEvent} using {@link ThingEvent#getType()}
     * @return true if allowed, false otherwise.
     */
    public static boolean isTypeAllowed(final String type) {
        return AVAILABLE_TYPES.contains(type);
    }
}
