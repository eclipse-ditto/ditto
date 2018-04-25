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

import static java.util.Objects.requireNonNull;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;

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
    private static final String PROP_NAME_ACTION = "action";
    private static final String PROP_NAME_SUBJECT = "subject";

    private final String namespace;
    private final String id;
    private final Group group;
    private final Channel channel;
    private final Criterion criterion;
    private final Action action;
    private final String path;
    private final String subject;

    private ImmutableTopicPath(final String namespace, final String id, final Group group, final Channel channel,
            final Criterion criterion, final Action action, final String subject) {
        this.namespace = namespace;
        this.id = id;
        this.group = group;
        this.channel = channel;
        this.criterion = criterion;
        this.action = action;
        this.subject = subject;
        this.path = buildPath();
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
        requireNonNull(namespace, PROP_NAME_NAMESPACE);
        requireNonNull(id, PROP_NAME_ID);
        requireNonNull(group, PROP_NAME_GROUP);
        requireNonNull(channel, PROP_NAME_CHANNEL);
        requireNonNull(criterion, PROP_NAME_CRITERION);

        return new ImmutableTopicPath(namespace, id, group, channel, criterion, null, null);
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
            final Channel channel,
            final Criterion criterion, final Action action) {
        requireNonNull(namespace, PROP_NAME_NAMESPACE);
        requireNonNull(id, PROP_NAME_ID);
        requireNonNull(group, PROP_NAME_GROUP);
        requireNonNull(channel, PROP_NAME_CHANNEL);
        requireNonNull(criterion, PROP_NAME_CRITERION);
        requireNonNull(action, PROP_NAME_ACTION);

        return new ImmutableTopicPath(namespace, id, group, channel, criterion, action, null);
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
            final Channel channel,
            final Criterion criterion, final String subject) {
        requireNonNull(namespace, PROP_NAME_NAMESPACE);
        requireNonNull(id, PROP_NAME_ID);
        requireNonNull(group, PROP_NAME_GROUP);
        requireNonNull(channel, PROP_NAME_CHANNEL);
        requireNonNull(criterion, PROP_NAME_CRITERION);
        requireNonNull(subject, PROP_NAME_SUBJECT);

        return new ImmutableTopicPath(namespace, id, group, channel, criterion, null, subject);
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
                Objects
                        .equals(subject, that.subject) && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, id, group, channel, criterion, action, path, subject);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "namespace=" + namespace + ", id=" + id + ", group=" + group +
                ", channel=" + channel
                + ", criterion=" + criterion + ", action=" + action + ", subject=" + subject + ", path=" + path + ']';
    }

    private String buildPath() {
        if (action != null) {
            // e.g.: <ns>/<id>/things/twin/commands/modify
            return MessageFormat.format("{0}/{1}/{2}/{3}/{4}/{5}", namespace, id, group, channel, criterion, action);
        } else if (subject != null) {
            // e.g.: <ns>/<id>/things/live/messages/<msgSubject>
            return MessageFormat.format("{0}/{1}/{2}/{3}/{4}/{5}", namespace, id, group, channel, criterion, subject);
        } else {
            // e.g.: <ns>/<id>/things/twin/search
            return MessageFormat.format("{0}/{1}/{2}/{3}/{4}", namespace, id, group, channel, criterion);
        }
    }

}
