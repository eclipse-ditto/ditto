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
package org.eclipse.ditto.protocoladapter;

/**
 * Builder to create a topic path for events.
 */
public interface EventsTopicPathBuilder extends TopicPathBuildable {

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.Action#CREATED}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    EventsTopicPathBuilder created();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.Action#MODIFIED}. A previously set action is
     * replaced.
     *
     * @return this builder to allow method chaining.
     */
    EventsTopicPathBuilder modified();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.Action#DELETED}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    EventsTopicPathBuilder deleted();
}
