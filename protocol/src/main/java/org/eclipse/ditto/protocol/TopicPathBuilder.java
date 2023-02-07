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
     * Sets the {@code Group} of this builder to {@link TopicPath.Group#CONNECTIONS}. A previously set group is
     * replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuilder connections();

    /**
     * Sets the {@code Group} of this builder to {@link TopicPath.Criterion#SEARCH}. A previously set group is
     * replaced.
     *
     * @return this builder to allow method chaining.
     */
    SearchTopicPathBuilder search();


    /**
     * Sets the {@code Criterion} of this builder to {@link TopicPath.Criterion#COMMANDS}. A previously set criterion is
     * replaced.
     *
     * @return this builder to allow method chaining.
     */
    CommandsTopicPathBuilder commands();

    /**
     * Sets the {@code Criterion} of this builder to {@link TopicPath.Criterion#ANNOUNCEMENTS}.
     *
     * @return this builder.
     * @since 2.0.0
     */
    AnnouncementsTopicPathBuilder announcements();

    /**
     * Sets the {@code Group} of this builder to {@link TopicPath.Criterion#STREAMING}. A previously set group is
     * replaced.
     *
     * @return this builder.
     * @since 3.2.0
     */
    StreamingTopicPathBuilder streaming();

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
     * Sets the {@code Channel} of this builder to {@link TopicPath.Channel#NONE}. A previously set channel is
     * replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuilder none();

    /**
     * Sets the {@code Criterion} of this builder to {@link TopicPath.Criterion#EVENTS}. A previously set criterion is
     * replaced.
     *
     * @return this builder to allow method chaining.
     */
    EventsTopicPathBuilder events();

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

    /**
     * Sets the {@code Criterion} of this builder to {@link TopicPath.Criterion#ACKS}. A previously set criterion is
     * replaced.
     *
     * @return this builder to allow method chaining.
     * @since 1.1.0
     */
    AcknowledgementTopicPathBuilder acks();

}
