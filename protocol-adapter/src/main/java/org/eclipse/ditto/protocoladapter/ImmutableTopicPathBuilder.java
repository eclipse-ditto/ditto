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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingIdInvalidException;

/**
 * Mutable implementation of {@link TopicPathBuilder} for building immutable {@link TopicPath} instances.
 */
@NotThreadSafe
final class ImmutableTopicPathBuilder implements TopicPathBuilder, MessagesTopicPathBuilder, EventsTopicPathBuilder,
        CommandsTopicPathBuilder {

    private static final Pattern THING_ID_PATTERN = Pattern.compile(Thing.ID_REGEX);

    private final String namespace;
    private final String id;

    private TopicPath.Group group;
    private TopicPath.Channel channel;
    private TopicPath.Criterion criterion;
    private TopicPath.Action action;
    private String subject;

    private ImmutableTopicPathBuilder(final String namespace, final String id) {
        this.namespace = namespace;
        this.id = id;
    }

    /**
     * Returns an empty {@code TopicPath}.
     *
     * @return the topic path.
     */
    public static TopicPath empty() {
        return EmptyTopicPath.newInstance();
    }

    /**
     * Returns a new TopicPathBuilder for the specified {@code thingId}. The {@code namespace} and {@code id} part of
     * the {@code TopicPath} will pe parsed from the {@code thingId} and set in the builder.
     *
     * @param thingId the Thing ID.
     * @return the builder.
     * @throws NullPointerException if {@code thingId} is {@code null}.
     * @throws ThingIdInvalidException if {@code thingId} is not in the expected format.
     */
    public static TopicPathBuilder of(final String thingId) {
        requireNonNull(thingId, "thing id");

        final Matcher matcher = THING_ID_PATTERN.matcher(thingId);
        final boolean matches = matcher.matches();

        if (!matches) {
            throw ThingIdInvalidException.newBuilder(thingId).build();
        }

        final String namespace = matcher.group("ns");
        final String id = matcher.group("id");

        return new ImmutableTopicPathBuilder(namespace, id);
    }

    /**
     * Returns a new TopicPathBuilder for the specified {@code namespace} and {@code id}.
     *
     * @param namespace the Namespace.
     * @param id the Id.
     * @return the builder.
     * @throws NullPointerException if {@code namespace} or {@code id} is {@code null}.
     */
    public static TopicPathBuilder of(final String namespace, final String id) {
        requireNonNull(namespace, ImmutableTopicPath.PROP_NAME_NAMESPACE);
        requireNonNull(id, ImmutableTopicPath.PROP_NAME_ID);

        return new ImmutableTopicPathBuilder(namespace, id);
    }

    @Override
    public TopicPathBuilder things() {
        this.group = TopicPath.Group.THINGS;
        return this;
    }

    @Override
    public TopicPathBuilder policies() {
        this.group = TopicPath.Group.POLICIES;
        return this;
    }

    @Override
    public CommandsTopicPathBuilder commands() {
        this.criterion = TopicPath.Criterion.COMMANDS;
        return this;
    }

    @Override
    public TopicPathBuilder twin() {
        this.channel = TopicPath.Channel.TWIN;
        return this;
    }

    @Override
    public TopicPathBuilder live() {
        this.channel = TopicPath.Channel.LIVE;
        return this;
    }


    @Override
    public EventsTopicPathBuilder events() {
        this.criterion = TopicPath.Criterion.EVENTS;
        return this;
    }

    @Override
    public TopicPathBuildable search() {
        this.criterion = TopicPath.Criterion.SEARCH;
        return this;
    }

    @Override
    public TopicPathBuildable errors() {
        this.criterion = TopicPath.Criterion.ERRORS;
        return this;
    }

    @Override
    public MessagesTopicPathBuilder messages() {
        this.criterion = TopicPath.Criterion.MESSAGES;
        return this;
    }

    @Override
    public CommandsTopicPathBuilder create() {
        this.action = TopicPath.Action.CREATE;
        return this;
    }

    @Override
    public CommandsTopicPathBuilder retrieve() {
        this.action = TopicPath.Action.RETRIEVE;
        return this;
    }

    @Override
    public CommandsTopicPathBuilder modify() {
        this.action = TopicPath.Action.MODIFY;
        return this;
    }

    @Override
    public CommandsTopicPathBuilder delete() {
        this.action = TopicPath.Action.DELETE;
        return this;
    }

    @Override
    public EventsTopicPathBuilder created() {
        this.action = TopicPath.Action.CREATED;
        return this;
    }

    @Override
    public EventsTopicPathBuilder modified() {
        this.action = TopicPath.Action.MODIFIED;
        return this;
    }

    @Override
    public EventsTopicPathBuilder deleted() {
        this.action = TopicPath.Action.DELETED;
        return this;
    }

    @Override
    public MessagesTopicPathBuilder subject(final String subject) {
        this.subject = subject;
        return this;
    }

    @Override
    public TopicPath build() {
        if (action != null && id != null) {
            return ImmutableTopicPath.of(namespace, id, group, channel, criterion, action);
        } else if (subject != null) {
            return ImmutableTopicPath.of(namespace, id, group, channel, criterion, subject);
        } else {
            return ImmutableTopicPath.of(namespace, id, group, channel, criterion);
        }
    }

    /**
     * Implementation of {@link TopicPath} with an empty path.
     */
    private static class EmptyTopicPath implements TopicPath {

        private EmptyTopicPath() {
            // no-op
        }

        static EmptyTopicPath newInstance() {
            return new EmptyTopicPath();
        }

        @Override
        public String getNamespace() {
            return null;
        }

        @Override
        public Group getGroup() {
            return null;
        }

        @Override
        public Criterion getCriterion() {
            return null;
        }

        @Override
        public Channel getChannel() {
            return null;
        }

        @Override
        public Optional<Action> getAction() {
            return Optional.empty();
        }

        @Override
        public Optional<String> getSubject() {
            return Optional.empty();
        }

        @Override
        public String getId() {
            return null;
        }

        @Override
        public String getPath() {
            return "";
        }

        @Override
        public boolean equals(final Object o) {
            return this == o || !(o == null || getClass() != o.getClass());
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

}
