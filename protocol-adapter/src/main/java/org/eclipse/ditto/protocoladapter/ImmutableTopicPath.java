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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link TopicPath}.
 */
@Immutable
final class ImmutableTopicPath implements TopicPath {

    static final String PROP_NAME_NAMESPACE = "namespace";
    static final String PROP_NAME_ID = "id";
    private static final String PROP_NAME_GROUP = "group";
    private static final String PROP_NAME_CHANNEL = "channel";
    private static final String PROP_NAME_CRITERION = "criterion";

    private final String namespace;
    private final String id;
    private final Group group;
    private final Channel channel;
    private final Criterion criterion;
    @Nullable private final Action action;
    @Nullable private final String subject;
    @Nullable private final SearchAction searchAction;
    private final String path;

    private ImmutableTopicPath(final String namespace, final String id, final Group group,
            @Nullable final Channel channel, final Criterion criterion, @Nullable final Action action, @Nullable final SearchAction searchAction,
            @Nullable final String subject) {
        this.namespace = checkNotNull(namespace, PROP_NAME_NAMESPACE);
        this.id = checkNotNull(id, PROP_NAME_ID);
        this.group = checkNotNull(group, PROP_NAME_GROUP);
        this.channel = checkChannelArgument(channel, group);
        this.criterion = checkNotNull(criterion, PROP_NAME_CRITERION);
        this.action = action;
        this.searchAction = searchAction;
        this.subject = subject;
        this.path = buildPath();
    }

    private Channel checkChannelArgument(final Channel channel, final Group group) {
        if (group == Group.POLICIES) {
            // for policies group no channel is required/allowed
            checkArgument(channel, ch -> ch == null || ch == Channel.NONE,
                    () -> "The policies group requires no channel.");
            return Channel.NONE;
        } else {
            // for other groups just check that a channel is there
            return checkNotNull(channel, PROP_NAME_CHANNEL);
        }
    }

    /**
     * Returns a new ImmutableTopicPath for the specified {@code namespace}, {@code id}, {@code group} and
     * {@code criterion}.
     *
     * @param namespace the namespace.
     * @param id the id.
     * @param group the group.
     * @param channel the channel.
     * @param criterion the criterion.
     * @return the TopicPath.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImmutableTopicPath of(final String namespace, final String id, final Group group,
            final Channel channel, final Criterion criterion) {
        return new ImmutableTopicPath(namespace, id, group, channel, criterion, null, null, null);
    }

    /**
     * Returns a new ImmutableTopicPath for the specified {@code namespace}, {@code id}, {@code group},
     * {@code criterion} and {@code action}.
     *
     * @param namespace the namespace.
     * @param id the id.
     * @param group the group.
     * @param channel the channel.
     * @param criterion the criterion.
     * @param action the action.
     * @return the TopicPath.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImmutableTopicPath of(final String namespace, final String id, final Group group,
            final Channel channel, final Criterion criterion, final Action action) {
        checkNotNull(action, "action");
        return new ImmutableTopicPath(namespace, id, group, channel, criterion, action, null, null);
    }


    /**
     * Returns a new ImmutableTopicPath for the specified {@code namespace}, {@code id}, {@code group},
     * {@code criterion} and {@code action}
     *
     * @param namespace the namespace.
     * @param id the id.
     * @param group the group.
     * @param channel the channel.
     * @param criterion the criterion.
     * @param subject the subject of the path.
     * @return the TopicPath.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImmutableTopicPath of(final String namespace, final String id, final Group group,
            final Channel channel, final Criterion criterion, final String subject) {
        checkNotNull(subject, "subject");
        return new ImmutableTopicPath(namespace, id, group, channel, criterion, null, null, subject);
    }

    /**
     * Returns a new ImmutableTopicPath for the specified {@code namespace}, {@code id}, {@code group},
     * {@code criterion} and {@code action}
     *
     * @param namespace the namespace.
     * @param id the id.
     * @param group the group.
     * @param channel the channel.
     * @param criterion the criterion.
     * @param searchAction the subject of the path.
     * @return the TopicPath.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImmutableTopicPath of(final String namespace, final String id, final Group group,
            final Channel channel, final Criterion criterion, final SearchAction searchAction) {
        checkNotNull(searchAction, "searchAction");
        return new ImmutableTopicPath(namespace, id, group, channel, criterion, null, searchAction, null);
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public Group getGroup() {
        return group;
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public Criterion getCriterion() {
        return criterion;
    }

    @Override
    public Optional<Action> getAction() {
        return Optional.ofNullable(action);
    }

    @Override
    public Optional<SearchAction> getSearchAction() {
        return Optional.ofNullable(searchAction);
    }

    @Override
    public Optional<String> getSubject() {
        return Optional.ofNullable(subject);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getPath() {
        return path;
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableTopicPath that = (ImmutableTopicPath) o;
        return Objects.equals(namespace, that.namespace) && Objects.equals(id, that.id) && group == that.group
                && channel == that.channel && criterion == that.criterion && Objects.equals(action, that.action) &&
                Objects.equals(searchAction, that.searchAction) &&
                Objects.equals(subject, that.subject) && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, id, group, channel, criterion, action, searchAction, path, subject);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "namespace=" + namespace + ", id=" + id + ", group=" + group +
                ", channel=" + channel
                + ", criterion=" + criterion + ", action=" + action + ", searchAction=" + searchAction + ", subject=" + subject + ", path=" + path + ']';
    }

    private String buildPath() {

        final String namespaceIdGroup = MessageFormat.format("{0}/{1}/{2}", namespace, id, group);
        final StringBuilder builder = new StringBuilder(namespaceIdGroup);

        // e.g. policy commands do not have a channel
        if (channel != Channel.NONE) {
            builder.append(PATH_DELIMITER).append(channel);
        }

        builder.append(PATH_DELIMITER).append(criterion);

        if (action != null) {
            // e.g.: <ns>/<id>/things/twin/commands/<action>
            builder.append(PATH_DELIMITER).append(action);
        } else if (subject != null) {
            // e.g.: <ns>/<id>/things/live/messages/<subject>
            builder.append(PATH_DELIMITER).append(subject);
        }else if (searchAction != null) {
            builder.append(PATH_DELIMITER).append(searchAction);
        }

        return builder.toString();
    }

}
