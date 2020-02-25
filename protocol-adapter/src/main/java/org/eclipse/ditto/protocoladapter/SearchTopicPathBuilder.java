/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter;

/**
 * Builder to create a topic path for commands.
 */
public interface SearchTopicPathBuilder extends TopicPathBuildable {

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.Action#SUBSCRIBE}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     * @since 1.2.0
     */
    TopicPathBuildable subscribe();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.Action#CANCEL}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     * @since 1.2.0
     */
    TopicPathBuildable cancel();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.Action#REQUEST}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     * @since 1.2.0
     */
    TopicPathBuildable request();

}