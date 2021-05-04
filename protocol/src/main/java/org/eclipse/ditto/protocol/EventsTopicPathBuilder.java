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
package org.eclipse.ditto.protocol;

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
     * Sets the {@code Action} of this builder to {@link TopicPath.Action#MERGED}. A previously set action is
     * replaced.
     *
     * @return this builder to allow method chaining.
     */
    EventsTopicPathBuilder merged();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.Action#DELETED}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    EventsTopicPathBuilder deleted();
}
