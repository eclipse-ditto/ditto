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

import java.util.List;

import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * Strategy to convert ThingEvents to the representation used to persist them to the database.
 *
 * @param <T> Type of the {@link ThingEvent}
 * @param <D> Type of the Updates for the Thing.
 * @param <P> Type of the Updates for the Policies of a Thing.
 */
public interface EventToPersistenceStrategy<T extends ThingEvent, D, P> {

    /**
     * Create all updates needed to persist the changes made by {@code event}.
     *
     * @param event The event.
     * @param indexLengthRestrictionEnforcer The length enforcer to restrict length before perstisting.
     * @return Te updates needed to persist the information of {@code event}.
     */
    List<D> thingUpdates(T event, final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer);

    /**
     * Create all updates needed to persist the policy changes made by {@code event}.
     *
     * @param event The event.
     * @param policyEnforcer The policy enforcer.
     * @return The updates needed to persist the policy information of {@code event}.
     */
    List<P> policyUpdates(T event, final PolicyEnforcer policyEnforcer);
}
