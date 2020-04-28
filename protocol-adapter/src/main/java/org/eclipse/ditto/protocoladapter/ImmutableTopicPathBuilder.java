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

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.entity.id.NamespacedEntityId;
import org.eclipse.ditto.model.things.ThingIdInvalidException;

/**
 * Mutable implementation of {@link TopicPathBuilder} for building immutable {@link TopicPath} instances.
 */
@NotThreadSafe
final class ImmutableTopicPathBuilder implements TopicPathBuilder, MessagesTopicPathBuilder, EventsTopicPathBuilder,
        CommandsTopicPathBuilder, AcknowledgementTopicPathBuilder, SearchTopicPathBuilder {

    private final String namespace;
    private final String name;

    private TopicPath.Group group;
    private TopicPath.Channel channel;
    private TopicPath.Criterion criterion;
    private TopicPath.Action action;
    private TopicPath.SearchAction searchAction;
    private String subject;

    private ImmutableTopicPathBuilder(final String namespace, final String name) {
        this.namespace = namespace;
        this.name = name;
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
     * Returns a new TopicPathBuilder for the specified {@code entityId}. The {@code namespace} and {@code id} part of
     * the {@code TopicPath} will pe parsed from the {@code entityId} and set in the builder.
     *
     * @param entityId the entity ID.
     * @return the builder.
     * @throws NullPointerException if {@code entityId} is {@code null}.
     * @throws ThingIdInvalidException if {@code entityId} is not in the expected format.
     */
    public static TopicPathBuilder of(final NamespacedEntityId entityId) {
        requireNonNull(entityId, "entityId");
        return new ImmutableTopicPathBuilder(entityId.getNamespace(), entityId.getName());
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
    public SearchTopicPathBuilder search() {
        this.criterion = TopicPath.Criterion.SEARCH;
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
    public TopicPathBuilder none() {
        this.channel = TopicPath.Channel.NONE;
        return this;
    }

    @Override
    public EventsTopicPathBuilder events() {
        this.criterion = TopicPath.Criterion.EVENTS;
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
    public AcknowledgementTopicPathBuilder acks() {
        this.criterion = TopicPath.Criterion.ACKS;
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
    public TopicPathBuildable subscribe() {
        this.searchAction = TopicPath.SearchAction.SUBSCRIBE;
        return this;
    }

    @Override
    public TopicPathBuildable cancel() {
        this.searchAction = TopicPath.SearchAction.CANCEL;
        return this;
    }

    @Override
    public TopicPathBuildable request() {
        this.searchAction = TopicPath.SearchAction.REQUEST;
        return this;
    }

    @Override
    public TopicPathBuildable complete() {
        this.searchAction = TopicPath.SearchAction.COMPLETE;
        return this;
    }

    @Override
    public TopicPathBuildable failed() {
        this.searchAction = TopicPath.SearchAction.FAILED;
        return this;
    }

    @Override
    public TopicPathBuildable hasNext() {
        this.searchAction = TopicPath.SearchAction.NEXT;
        return this;
    }

    @Override
    public EventsTopicPathBuilder generated() {
        this.searchAction = TopicPath.SearchAction.GENERATED;
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
        this.subject = checkNotNull(subject, "subject");
        return this;
    }

    @Override
    public AcknowledgementTopicPathBuilder label(final CharSequence label) {
        subject = checkNotNull(label, "label").toString();
        return this;
    }

    @Override
    public AcknowledgementTopicPathBuilder aggregatedAcks() {
        subject = null;
        return this;
    }

    @Override
    public TopicPath build() {
        if (action != null && name != null) {
            return ImmutableTopicPath.of(namespace, name, group, channel, criterion, action);
        } else if (subject != null) {
            return ImmutableTopicPath.of(namespace, name, group, channel, criterion, subject);
        } else if (searchAction != null) {
            return ImmutableTopicPath.of(namespace, name, group, channel, criterion, searchAction);
        } else {
            return ImmutableTopicPath.of(namespace, name, group, channel, criterion);
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
        public Optional<SearchAction> getSearchAction() {
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
