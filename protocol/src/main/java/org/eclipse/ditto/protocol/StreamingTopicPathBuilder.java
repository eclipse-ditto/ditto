/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
 * Builder to create a topic path for streaming commands.
 *
 * @since 3.2.0
 */
public interface StreamingTopicPathBuilder extends TopicPathBuildable {

    /**
     * Sets the {@code Action} of this builder to the passed {@code subscribingCommandName}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuildable subscribe(String subscribingCommandName);

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.StreamingAction#CANCEL}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuildable cancel();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.StreamingAction#REQUEST}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuildable request();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.StreamingAction#COMPLETE}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuildable complete();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.StreamingAction#FAILED}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuildable failed();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.StreamingAction#NEXT}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuildable hasNext();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.StreamingAction#GENERATED}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuildable generated();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.StreamingAction#ERROR}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuildable error();

}
