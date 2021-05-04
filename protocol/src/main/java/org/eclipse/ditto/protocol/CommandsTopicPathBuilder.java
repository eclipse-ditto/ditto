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
 * Builder to create a topic path for commands.
 */
public interface CommandsTopicPathBuilder extends TopicPathBuildable {

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.Action#CREATE}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    CommandsTopicPathBuilder create();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.Action#RETRIEVE}. A previously set action is
     * replaced.
     *
     * @return this builder to allow method chaining.
     */
    CommandsTopicPathBuilder retrieve();


    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.Action#MODIFY}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    CommandsTopicPathBuilder modify();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.Action#MERGE}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    CommandsTopicPathBuilder merge();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.Action#DELETE}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    CommandsTopicPathBuilder delete();

}
