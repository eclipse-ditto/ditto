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

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.signals.commands.streaming.CancelStreamingSubscription;
import org.eclipse.ditto.base.model.signals.commands.streaming.RequestFromStreamingSubscription;
import org.eclipse.ditto.base.model.signals.commands.streaming.SubscribeForPersistedEvents;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionComplete;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionCreated;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionFailed;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionHasNext;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityConstants;
import org.eclipse.ditto.policies.model.PolicyConstants;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CancelSubscription;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CreateSubscription;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.RequestFromSubscription;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionComplete;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionCreated;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionFailed;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionHasNextPage;

/**
 * Represents the path of a topic for the Ditto Protocol.
 * <p>
 * A {@code TopicPath} complies with the scheme
 *
 * {@code <namespace>/<id>/<group>/<channel>/<criterion>/<action>} <br>
 * for example {@code org.eclipse.ditto/myThing/things/twin/commands/modify}
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
     * Returns a mutable builder to create immutable {@code TopicPath} instances for a given {@code connectionId}.
     *
     * @param connectionId the identifier of the {@code Connection}.
     * @return the builder.
     * @throws NullPointerException if {@code connectionId} is {@code null}.
     * @since 3.1.0
     */
    static TopicPathBuilder newBuilder(final ConnectionId connectionId) {
        return ProtocolFactory.newTopicPathBuilder(connectionId);
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
     * Returns the entity name part of this {@code TopicPath}.
     *
     * @return the entity name.
     */
    String getEntityName();

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
     * Returns an {@link Optional} for a search action part of this {@code TopicPath}.
     *
     * @return the search action.
     */
    Optional<SearchAction> getSearchAction();

    /**
     * Returns an {@link Optional} for a streaming action part of this {@code TopicPath}.
     *
     * @return the streaming action.
     * @since 3.2.0
     */
    Optional<StreamingAction> getStreamingAction();

    /**
     * Returns an {@link Optional} for a subject part of this {@code TopicPath}.
     *
     * @return the subject.
     */
    Optional<String> getSubject();

    /**
     * Returns the path of this {@code TopicPath}.
     *
     * @return the path.
     */
    String getPath();

    default boolean isWildcardTopic() {
        return ID_PLACEHOLDER.equals(getEntityName());
    }

    /**
     * Indicates whether this TopicPath has the specified Group.
     *
     * @param expectedGroup the group to check for.
     * @return {@code true} if this TopicPath has group {@code expectedGroup}, {@code false} else.
     * @since 2.0.0
     */
    boolean isGroup(@Nullable Group expectedGroup);

    /**
     * Indicates whether this TopicPath has the specified Channel.
     *
     * @param expectedChannel the channel to check for.
     * @return {@code true} if this TopicPath has channel {@code expectedChannel}, {@code false} else.
     * @since 2.0.0
     */
    boolean isChannel(@Nullable Channel expectedChannel);

    /**
     * Indicates whether this TopicPath has the specified Criterion.
     *
     * @param expectedCriterion the criterion to check for.
     * @return {@code true} if this TopicPath has criterion {@code expectedCriterion}, {@code false} else.
     * @since 2.0.0
     */
    boolean isCriterion(@Nullable Criterion expectedCriterion);

    /**
     * Indicates whether this TopicPath has the specified Action.
     *
     * @param expectedAction the action to check for.
     * @return {@code true} if this TopicPath has action {@code expectedAction}, {@code false} else.
     * @since 2.0.0
     */
    boolean isAction(@Nullable TopicPath.Action expectedAction);

    /**
     * An enumeration of topic path groups.
     */
    enum Group {

        POLICIES("policies", PolicyConstants.ENTITY_TYPE),

        THINGS("things", ThingConstants.ENTITY_TYPE),

        /**
         * Connections group.
         *
         * @since 2.1.0
         */
        CONNECTIONS("connections", ConnectivityConstants.ENTITY_TYPE);

        private final String name;
        private final EntityType entityType;

        Group(final String name, final EntityType entityType) {
            this.name = name;
            this.entityType = entityType;
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

        public EntityType getEntityType() {
            return entityType;
        }

        /**
         * @return the same as {@link #getName()}.
         */
        @Override
        public String toString() {
            return getName();
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
        ACKS("acks"),

        /**
         * Criterion for announcements.
         *
         * @since 2.0.0
         */
        ANNOUNCEMENTS("announcements"),

        /**
         * Criterion for streaming commands.
         *
         * @since 3.2.0
         */
        STREAMING("streaming");

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

        /**
         * @return the same as {@link #getName()}.
         */
        @Override
        public String toString() {
            return getName();
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

        /**
         * @return same as {@link #getName()}.
         */
        @Override
        public String toString() {
            return getName();
        }

    }

    /**
     * An enumeration of topic path actions.
     */
    enum Action {

        CREATE("create"),

        RETRIEVE("retrieve"),

        MODIFY("modify"),

        MERGE("merge"),

        DELETE("delete"),


        CREATED("created"),

        MODIFIED("modified"),

        MERGED("merged"),

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

        NEXT(SubscriptionHasNextPage.NAME),

        /**
         * SearchAction for search errors.
         *
         * @since 2.0.0
         */
        ERROR("error");

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
            return Stream.of(values())
                    .filter(a -> Objects.equals(a.getName(), name))
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

        /**
         * @return the same as {@link #getName()}.
         */
        @Override
        public String toString() {
            return getName();
        }

    }

    /**
     * An enumeration of topic path streaming actions.
     *
     * @since 3.2.0
     */
    enum StreamingAction {

        SUBSCRIBE_FOR_PERSISTED_EVENTS(SubscribeForPersistedEvents.NAME),

        CANCEL(CancelStreamingSubscription.NAME),

        REQUEST(RequestFromStreamingSubscription.NAME),

        COMPLETE(StreamingSubscriptionComplete.NAME),

        GENERATED(StreamingSubscriptionCreated.NAME),

        FAILED(StreamingSubscriptionFailed.NAME),

        NEXT(StreamingSubscriptionHasNext.NAME),

        ERROR("error");

        private final String name;

        StreamingAction(final String name) {
            this.name = name;
        }

        /**
         * Creates a StreamingAction from the passed StreamingAction {@code name} if such an enum value exists,
         * otherwise an empty Optional.
         *
         * @param name the StreamingAction name to create the StreamingAction enum value of.
         * @return the optional StreamingAction.
         */
        public static Optional<StreamingAction> forName(final String name) {
            return Stream.of(values())
                    .filter(a -> Objects.equals(a.getName(), name))
                    .findFirst();
        }

        /**
         * Returns the StreamingAction name as String.
         *
         * @return the StreamingAction name as String.
         */
        public String getName() {
            return name;
        }

        /**
         * @return the same as {@link #getName()}.
         */
        @Override
        public String toString() {
            return getName();
        }

    }

}
