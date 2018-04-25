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

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Represents the path of a topic for the Ditto Protocol.
 * <p>
 * A {@code TopicPath} complies with the scheme
 *
 * <tt>&lt;namespace&gt;/&lt;id&gt;/&lt;group&gt;/&lt;channel&gt;/&lt;criterion&gt;/&lt;action&gt;</tt> <br>
 * for example <tt>org.eclipse.ditto/myThing/things/twin/commands/modify</tt>
 * </p>
 */
public interface TopicPath {

    String ID_PLACEHOLDER = "_";
    /**
     * Returns a mutable builder to create immutable {@code TopicPath} instances for a given {@code thingId}.
     *
     * @param thingId the identifier of the {@code Thing}.
     * @return the builder.
     * @throws NullPointerException if {@code thingId} is {@code null}.
     */
    static TopicPathBuilder newBuilder(final String thingId) {
        return ProtocolFactory.newTopicPathBuilder(thingId);
    }

    /**
     * Returns a mutable builder to create immutable {@code TopicPath} instances for a given {@code namespace}.
     *
     * @param namespace the namespace.
     * @return the builder.
     * @throws NullPointerException if {@code namespace} is {@code null}.
     */
    static TopicPathBuilder fromNamespace(final String namespace) {
        return ProtocolFactory.newTopicPathBuilderFromNamespace(namespace);
    }

    /**
     * Returns the namespace part of this {@code TopicPath}.
     *
     * @return the namespace.
     */
    String getNamespace();

    /**
     * Returns the group part of this {@code TopicPath}.
     *
     * @return the group.
     */
    Group getGroup();

    /**
     * Returns the channel part of this {@code TopicPath}.
     *
     * @return the channel.
     */
    Channel getChannel();

    /**
     * Returns the criterion part of this {@code TopicPath}.
     *
     * @return the criterion.
     */
    Criterion getCriterion();

    /**
     * Returns an {@link Optional} for an action part of this {@code TopicPath}.
     *
     * @return the action.
     */
    Optional<Action> getAction();

    /**
     * Returns an {@link Optional} for a subject part of this {@code TopicPath}.
     *
     * @return the subject.
     */
    Optional<String> getSubject();

    /**
     * Returns the id part of this {@code TopicPath}.
     *
     * @return the id.
     */
    String getId();

    /**
     * Returns the path of this {@code TopicPath}.
     *
     * @return the path.
     */
    String getPath();

    default boolean isWildcardTopic() {
        return ID_PLACEHOLDER.equals(getId());
    }

    /**
     * An enumeration of topic path groups.
     */
    enum Group {

        POLICIES("policies"),

        THINGS("things");

        private final String name;

        Group(final String name) {
            this.name = name;
        }

        /**
         * Creates a Group from the passed Group {@code name} if such an enum value exists, otherwise an empty Optional.
         *
         * @param name the Group name to create the Group enum value of.
         * @return the optional Group.
         */
        public static Optional<Group> forName(final String name) {
            return Stream.of(values()) //
                    .filter(a -> Objects.equals(a.getName(), name)) //
                    .findFirst();
        }

        /**
         * Returns the Group name as String.
         *
         * @return the Group name as String.
         */
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * An enumeration of topic path criteria.
     */
    enum Criterion {

        COMMANDS("commands"),

        EVENTS("events"),

        SEARCH("search"),

        MESSAGES("messages"),

        ERRORS("errors");

        private final String name;

        Criterion(final String name) {
            this.name = name;
        }

        /**
         * Creates a Criterion from the passed Criterion {@code name} if such an enum value exists, otherwise an empty
         * Optional.
         *
         * @param name the Criterion name to create the Criterion enum value of.
         * @return the optional Criterion.
         */
        public static Optional<Criterion> forName(final String name) {
            return Stream.of(values()) //
                    .filter(a -> Objects.equals(a.getName(), name)) //
                    .findFirst();
        }

        /**
         * Returns the Criterion name as String.
         *
         * @return the Criterion name as String.
         */
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * An enumeration of topic path channel.
     */
    enum Channel {

        TWIN("twin"),

        LIVE("live");

        private final String name;

        Channel(final String name) {
            this.name = name;
        }

        /**
         * Creates a Channel from the passed Channel {@code name} if such an enum value exists, otherwise an empty
         * Optional.
         *
         * @param name the Channel name to create the Channel enum value of.
         * @return the optional Channel.
         */
        public static Optional<Channel> forName(final String name) {
            return Stream.of(values()) //
                    .filter(a -> Objects.equals(a.getName(), name)) //
                    .findFirst();
        }

        /**
         * Returns the Channel name as String.
         *
         * @return the Channel name as String.
         */
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * An enumeration of topic path actions.
     */
    enum Action {

        CREATE("create"),

        RETRIEVE("retrieve"),

        MODIFY("modify"),

        DELETE("delete"),


        CREATED("created"),

        MODIFIED("modified"),

        DELETED("deleted");

        private final String name;

        Action(final String name) {
            this.name = name;
        }

        /**
         * Creates a Action from the passed Action {@code name} if such an enum value exists, otherwise an empty
         * Optional.
         *
         * @param name the Action name to create the Action enum value of.
         * @return the optional Action.
         */
        public static Optional<Action> forName(final String name) {
            return Stream.of(values()) //
                    .filter(a -> Objects.equals(a.getName(), name)) //
                    .findFirst();
        }

        /**
         * Returns the Action name as String.
         *
         * @return the Action name as String.
         */
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
