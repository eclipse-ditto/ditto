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
     * Sets the {@code Action} of this builder to {@link TopicPath.Action#DELETE}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    CommandsTopicPathBuilder delete();
}
