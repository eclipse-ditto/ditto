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
package org.eclipse.ditto.services.authorization.util.update;

/**
 * A strategy for cache updates.
 * @param <T> the type of events to be handled by this strategy.
 */
public interface CacheUpdateStrategy<T> {

    /**
     * The topic of the events.
     * @return the topic.
     */
    String getEventTopic();

    /**
     * The common java base class of the events to be handled.
     * @return the java class.
     */
    Class<T> getEventClass();

    /**
     * The event handler.
     * @param event the event handler.
     */
    void handleEvent(T event);
}
