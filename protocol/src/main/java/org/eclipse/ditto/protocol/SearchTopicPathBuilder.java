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
package org.eclipse.ditto.protocol;

/**
 * Builder to create a topic path for commands.
 */
public interface SearchTopicPathBuilder extends TopicPathBuildable {

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.SearchAction#SUBSCRIBE}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     * @since 1.2.0
     */
    TopicPathBuildable subscribe();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.SearchAction#CANCEL}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     * @since 1.2.0
     */
    TopicPathBuildable cancel();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.SearchAction#REQUEST}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     * @since 1.2.0
     */
    TopicPathBuildable request();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.SearchAction#COMPLETE}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     * @since 1.2.0
     */
    TopicPathBuildable complete();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.SearchAction#FAILED}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     * @since 1.2.0
     */
    TopicPathBuildable failed();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.SearchAction#NEXT}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     * @since 1.2.0
     */
    TopicPathBuildable hasNext();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.SearchAction#GENERATED}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     * @since 1.2.0
     */
    TopicPathBuildable generated();

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.SearchAction#ERROR}. A previously set action is replaced.
     *
     * @return this builder to allow method chaining.
     * @since 2.0.0
     */
    TopicPathBuildable error();

}
