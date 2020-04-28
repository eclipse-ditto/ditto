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
package org.eclipse.ditto.protocoladapter;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.RequestFromSubscription;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionComplete;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionCreated;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionFailed;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionHasNextPage;

/**
 * Represents the path of a topic for the Ditto Protocol.
 * <p>
 * A {@code TopicPath} complies with the scheme
 *
 * <code>&lt;namespace&gt;/&lt;id&gt;/&lt;group&gt;/&lt;channel&gt;/&lt;criterion&gt;/&lt;action&gt;</code> <br>
 * for example <code>org.eclipse.ditto/myThing/things/twin/commands/modify</code>
 * </p>
 */
public interface TopicPath {

    String ID_PLACEHOLDER = "_";
    String PATH_DELIMITER = "/";

    /**
     * Returns a mutable builder to create immutable {@code TopicPath} instances for a given {@code thingId}.
     *
     * @param thingId the identifier of the {@code Thing}.
     * @return the builder.
     * @throws NullPointerException if {@code thingId} is {@code null}.
     * @deprecated Thing ID is now typed. Use {@link #newBuilder(org.eclipse.ditto.model.things.ThingId)} instead.
     */
    @Deprecated
    static TopicPathBuilder newBuilder(final String thingId) {
        return newBuilder(ThingId.of(thingId));
    }

    /**
     * Returns a mutable builder to create immutable {@code TopicPath} instances for a given {@code thingId}.
     *
     * @param thingId the identifier of the {@code Thing}.
     * @return the builder.
     * @throws NullPointerException if {@code thingId} is {@code null}.
     */
    static TopicPathBuilder newBuilder(final ThingId thingId) {
        return ProtocolFactory.newTopicPathBuilder(thingId);
    }

    /**
     * Returns a mutable builder to create immutable {@code TopicPath} instances for a given {@code policyId}.
     *
     * @param policyId the identifier of the {@code Policy}.
     * @return the builder.
     * @throws NullPointerException if {@code policyId} is {@code null}.
     */
    static TopicPathBuilder newBuilder(final PolicyId policyId) {
        return ProtocolFactory.newTopicPathBuilder(policyId);
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
     * Returns an {@link Optional} for an search action part of this {@code TopicPath}.
     *
     * @return the search action.
     */
    Optional<SearchAction> getSearchAction();

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
            return Stream.of(values())
                    .filter(a -> Objects.equals(a.getName(), name))
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

        ERRORS("errors"),

        /**
         * Criterion for the topic path of an acknowledgement (ACK).
         *
         * @since 1.1.0
         */
        ACKS("acks");

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
            return Stream.of(values())
                    .filter(a -> Objects.equals(a.getName(), name))
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

        LIVE("live"),

        NONE("none");

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
            return Stream.of(values())
                    .filter(a -> Objects.equals(a.getName(), name))
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
            return Stream.of(values())
                    .filter(a -> Objects.equals(a.getName(), name))
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

    /**
     * An enumeration of topic path search-actions.
     *
     * @since 1.2.0
     */
    enum SearchAction {


        SUBSCRIBE(CreateSubscription.NAME),

        CANCEL(CancelSubscription.NAME),

        REQUEST(RequestFromSubscription.NAME),


        COMPLETE(SubscriptionComplete.NAME),

        GENERATED(SubscriptionCreated.NAME),

        FAILED(SubscriptionFailed.NAME),

        NEXT(SubscriptionHasNextPage.NAME);

        private final String name;

        SearchAction(final String name) {
            this.name = name;
        }

        /**
         * Creates a SearchAction from the passed SearchAction {@code name} if such an enum value exists, otherwise an empty
         * Optional.
         *
         * @param name the SearchAction name to create the SearchAction enum value of.
         * @return the optional SearchAction.
         * @since 1.2.0
         */
        public static Optional<SearchAction> forName(final String name) {
            return Stream.of(values()) //
                    .filter(a -> Objects.equals(a.getName(), name)) //
                    .findFirst();
        }

        /**
         * Returns the SearchAction name as String.
         *
         * @return the SearchAction name as String.
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
