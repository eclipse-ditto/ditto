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
 * A builder to create {@link TopicPath} instances.
 */
public interface TopicPathBuilder {

    /**
     * Sets the {@code Group} of this builder to {@link TopicPath.Group#THINGS}. A previously set group is replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuilder things();

    /**
     * Sets the {@code Group} of this builder to {@link TopicPath.Group#POLICIES}. A previously set group is replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuilder policies();


    /**
     * Sets the {@code Criterion} of this builder to {@link TopicPath.Criterion#COMMANDS}. A previously set criterion is
     * replaced.
     *
     * @return this builder to allow method chaining.
     */
    CommandsTopicPathBuilder commands();

    /**
     * Sets the {@code Channel} of this builder to {@link TopicPath.Channel#TWIN}. A previously set channel is
     * replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuilder twin();

    /**
     * Sets the {@code Channel} of this builder to {@link TopicPath.Channel#LIVE}. A previously set channel is
     * replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuilder live();

    /**
     * Sets the {@code Criterion} of this builder to {@link TopicPath.Criterion#EVENTS}. A previously set criterion is
     * replaced.
     *
     * @return this builder to allow method chaining.
     */
    EventsTopicPathBuilder events();

    /**
     * Sets the {@code Criterion} of this builder to {@link TopicPath.Criterion#SEARCH}. A previously set action is
     * replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuildable search();

    /**
     * Sets the {@code Criterion} of this builder to {@link TopicPath.Criterion#ERRORS}. A previously set action is
     * replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuildable errors();

    /**
     * Sets the {@code Criterion} of this builder to {@link TopicPath.Criterion#MESSAGES}. A previously set criterion is
     * replaced.
     *
     * @return this builder to allow method chaining.
     */
    MessagesTopicPathBuilder messages();

}
