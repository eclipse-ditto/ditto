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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.signals.commands.streaming.SubscribeForPersistedEvents;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.adapter.UnknownTopicPathException;

/**
 * Immutable implementation of {@link TopicPath}.
 */
@Immutable
final class ImmutableTopicPath implements TopicPath {

    private final String namespace;
    private final String name;
    private final Group group;
    private final Channel channel;
    private final Criterion criterion;
    @Nullable private final Action action;
    @Nullable private final SearchAction searchAction;
    @Nullable private final StreamingAction streamingAction;
    @Nullable private final String subject;

    private ImmutableTopicPath(final Builder builder) {
        namespace = builder.namespace;
        name = builder.name;
        group = builder.group;
        channel = builder.channel;
        criterion = builder.criterion;
        action = builder.action;
        searchAction = builder.searchAction;
        streamingAction = builder.streamingAction;
        subject = builder.subject;
    }

    /**
     * Returns a new builder with a fluent step API to create an {@code ImmutableTopicPath}.
     *
     * @param namespace the namespace part of the topic path to be built.
     * @param entityName the entity name part of the topic path to be built.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static TopicPathBuilder newBuilder(final String namespace, final String entityName) {
        checkNotNull(namespace, "namespace");
        checkNotNull(entityName, "entityName");
        return new Builder(namespace, entityName);
    }

    /**
     * Parses the string argument as a {@code ImmutableTopicPath}.
     *
     * @param topicPathString a String containing the ImmutableTopicPath representation to be parsed.
     * @return the {@code ImmutableTopicPath} represented by the argument.
     * @throws NullPointerException if {@code topicPathString} is {@code null}.
     * @throws UnknownTopicPathException if the string does not contain a parsable ImmutableTopicPath.
     */
    static ImmutableTopicPath parseTopicPath(final String topicPathString) {
        final TopicPathParser topicPathParser = new TopicPathParser(checkNotNull(topicPathString, "topicPathString"));
        return topicPathParser.get();
    }

    static JsonPointer newTopicOrPathPointer(final String path) {
        final String slash = TopicPath.PATH_DELIMITER;
        if (path.isEmpty() || slash.equals(path)) {
            return JsonPointer.empty();
        }
        final List<JsonKey> jsonKeys = new ArrayList<>();
        int segmentStart = path.startsWith(slash) ? 1 : 0;
        int segmentEnd = path.indexOf(slash, segmentStart);
        // add segments until double slashes are encountered
        while (segmentEnd >= 0 && segmentStart != segmentEnd) {
            jsonKeys.add(JsonKey.of(path.substring(segmentStart, segmentEnd)));
            segmentStart = segmentEnd + 1;
            segmentEnd = path.indexOf(slash, segmentStart);
        }
        if (segmentStart < path.length()) {
            jsonKeys.add(JsonKey.of(path.substring(segmentStart)));
        }
        // jsonKeys guaranteed to be non-empty due to the emptiness check at the start
        return JsonFactory.newPointer(jsonKeys.get(0), jsonKeys.stream().skip(1).toArray(JsonKey[]::new));
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
    public Optional<StreamingAction> getStreamingAction() {
        return Optional.ofNullable(streamingAction);
    }

    @Override
    public Optional<String> getSubject() {
        return Optional.ofNullable(subject);
    }

    @Override
    public String getEntityName() {
        return name;
    }

    @Override
    public String getPath() {
        final Stream<String> pathPartStream = Stream.<String>builder()
                .add(namespace)
                .add(name)
                .add(group.getName())
                .add(Channel.NONE != channel ? channel.getName() : null)
                .add(criterion.getName())
                .add(getStringOrNull(action))
                .add(getStringOrNull(searchAction))
                .add(getStringOrNull(streamingAction))
                .add(getStringOrNull(subject))
                .build();
        return pathPartStream.filter(Objects::nonNull).collect(Collectors.joining(PATH_DELIMITER));
    }

    @Nullable
    private static String getStringOrNull(@Nullable final Object pathPart) {
        @Nullable final String result;
        if (null == pathPart) {
            result = null;
        } else {
            result = pathPart.toString();
        }
        return result;
    }

    @Override
    public boolean isGroup(@Nullable final Group expectedGroup) {
        return group.equals(expectedGroup);
    }

    @Override
    public boolean isChannel(@Nullable final Channel expectedChannel) {
        return channel.equals(expectedChannel);
    }

    @Override
    public boolean isCriterion(@Nullable final Criterion expectedCriterion) {
        return criterion.equals(expectedCriterion);
    }

    @Override
    public boolean isAction(@Nullable final Action expectedAction) {
        return Objects.equals(action, expectedAction);
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
        return Objects.equals(namespace, that.namespace) &&
                Objects.equals(name, that.name) &&
                group == that.group &&
                channel == that.channel &&
                criterion == that.criterion &&
                Objects.equals(action, that.action) &&
                Objects.equals(searchAction, that.searchAction) &&
                Objects.equals(streamingAction, that.streamingAction) &&
                Objects.equals(subject, that.subject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, name, group, channel, criterion, action, searchAction, streamingAction,
                subject);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " ["
                + "namespace=" + namespace +
                ", name=" + name +
                ", group=" + group +
                ", channel=" + channel +
                ", criterion=" + criterion +
                ", action=" + action +
                ", searchAction=" + searchAction +
                ", streamingAction=" + streamingAction +
                ", subject=" + subject +
                ", path=" + getPath() +
                "]";
    }

    /**
     * Mutable implementation of {@link TopicPathBuilder} for building immutable {@link TopicPath} instances.
     */
    @NotThreadSafe
    private static final class Builder
            implements TopicPathBuilder, MessagesTopicPathBuilder, EventsTopicPathBuilder, CommandsTopicPathBuilder,
            AcknowledgementTopicPathBuilder, SearchTopicPathBuilder, AnnouncementsTopicPathBuilder,
            StreamingTopicPathBuilder {

        private final String namespace;
        private final String name;

        private Group group;
        private Channel channel;
        private Criterion criterion;
        @Nullable private Action action;
        @Nullable private SearchAction searchAction;
        @Nullable private StreamingAction streamingAction;
        @Nullable private String subject;

        private Builder(final String namespace, final String name) {
            this.namespace = namespace;
            this.name = name;
            group = null;
            channel = Channel.NONE;
            criterion = null;
            action = null;
            searchAction = null;
            streamingAction = null;
            subject = null;
        }

        @Override
        public TopicPathBuilder things() {
            group = Group.THINGS;
            return this;
        }

        @Override
        public TopicPathBuilder policies() {
            group = Group.POLICIES;
            return this;
        }

        @Override
        public TopicPathBuilder connections() {
            group = Group.CONNECTIONS;
            return this;
        }

        @Override
        public TopicPathBuilder twin() {
            channel = Channel.TWIN;
            return this;
        }

        @Override
        public TopicPathBuilder live() {
            channel = Channel.LIVE;
            return this;
        }

        @Override
        public TopicPathBuilder none() {
            channel = Channel.NONE;
            return this;
        }

        @Override
        public SearchTopicPathBuilder search() {
            criterion = Criterion.SEARCH;
            return this;
        }

        @Override
        public CommandsTopicPathBuilder commands() {
            criterion = Criterion.COMMANDS;
            return this;
        }

        @Override
        public AnnouncementsTopicPathBuilder announcements() {
            criterion = Criterion.ANNOUNCEMENTS;
            return this;
        }

        @Override
        public StreamingTopicPathBuilder streaming() {
            criterion = Criterion.STREAMING;
            return this;
        }

        @Override
        public EventsTopicPathBuilder events() {
            criterion = Criterion.EVENTS;
            return this;
        }

        @Override
        public TopicPathBuildable errors() {
            criterion = Criterion.ERRORS;
            return this;
        }

        @Override
        public MessagesTopicPathBuilder messages() {
            criterion = Criterion.MESSAGES;
            return this;
        }

        @Override
        public AcknowledgementTopicPathBuilder acks() {
            criterion = Criterion.ACKS;
            return this;
        }

        @Override
        public CommandsTopicPathBuilder create() {
            action = Action.CREATE;
            return this;
        }

        @Override
        public CommandsTopicPathBuilder retrieve() {
            action = Action.RETRIEVE;
            return this;
        }

        @Override
        public CommandsTopicPathBuilder modify() {
            action = Action.MODIFY;
            return this;
        }

        @Override
        public CommandsTopicPathBuilder merge() {
            action = Action.MERGE;
            return this;
        }

        @Override
        public CommandsTopicPathBuilder delete() {
            action = Action.DELETE;
            return this;
        }

        @Override
        public TopicPathBuildable subscribe() {
            searchAction = SearchAction.SUBSCRIBE;
            return this;
        }

        @Override
        public TopicPathBuildable subscribe(final String subscribingCommandName) {
            if (subscribingCommandName.equals(SubscribeForPersistedEvents.NAME)) {
                streamingAction = StreamingAction.SUBSCRIBE_FOR_PERSISTED_EVENTS;
            } else {
                throw UnknownCommandException.newBuilder(subscribingCommandName).build();
            }
            return this;
        }

        @Override
        public TopicPathBuildable cancel() {
            if (criterion == Criterion.SEARCH) {
                searchAction = SearchAction.CANCEL;
            } else if (criterion == Criterion.STREAMING) {
                streamingAction = StreamingAction.CANCEL;
            }
            return this;
        }

        @Override
        public TopicPathBuildable request() {
            if (criterion == Criterion.SEARCH) {
                searchAction = SearchAction.REQUEST;
            } else if (criterion == Criterion.STREAMING) {
                streamingAction = StreamingAction.REQUEST;
            }
            return this;
        }

        @Override
        public TopicPathBuildable complete() {
            if (criterion == Criterion.SEARCH) {
                searchAction = SearchAction.COMPLETE;
            } else if (criterion == Criterion.STREAMING) {
                streamingAction = StreamingAction.COMPLETE;
            }
            return this;
        }

        @Override
        public TopicPathBuildable failed() {
            if (criterion == Criterion.SEARCH) {
                searchAction = SearchAction.FAILED;
            } else if (criterion == Criterion.STREAMING) {
                streamingAction = StreamingAction.FAILED;
            }
            return this;
        }

        @Override
        public TopicPathBuildable hasNext() {
            if (criterion == Criterion.SEARCH) {
                searchAction = SearchAction.NEXT;
            } else if (criterion == Criterion.STREAMING) {
                streamingAction = StreamingAction.NEXT;
            }
            return this;
        }

        @Override
        public EventsTopicPathBuilder generated() {
            if (criterion == Criterion.SEARCH) {
                searchAction = SearchAction.GENERATED;
            } else if (criterion == Criterion.STREAMING) {
                streamingAction = StreamingAction.GENERATED;
            }
            return this;
        }

        @Override
        public TopicPathBuildable error() {
            if (criterion == Criterion.SEARCH) {
                searchAction = SearchAction.ERROR;
            } else if (criterion == Criterion.STREAMING) {
                streamingAction = StreamingAction.ERROR;
            }
            return this;
        }

        @Override
        public EventsTopicPathBuilder created() {
            action = Action.CREATED;
            return this;
        }

        @Override
        public EventsTopicPathBuilder modified() {
            action = Action.MODIFIED;
            return this;
        }

        @Override
        public EventsTopicPathBuilder merged() {
            action = Action.MERGED;
            return this;
        }

        @Override
        public EventsTopicPathBuilder deleted() {
            action = Action.DELETED;
            return this;
        }

        @Override
        public MessagesTopicPathBuilder subject(final String subject) {
            this.subject = checkNotNull(subject, "subject");
            return this;
        }

        @Override
        public AnnouncementsTopicPathBuilder name(final String name) {
            subject = checkNotNull(name, "name");
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
        public ImmutableTopicPath build() {
            validateChannel();
            return new ImmutableTopicPath(this);
        }

        private void validateChannel() {
            if ((Group.POLICIES == group || Group.CONNECTIONS == group) &&
                    Channel.NONE != channel) {
                throw new IllegalStateException("The policies and connection groups require no channel.");
            }
        }

    }

    @NotThreadSafe
    private static final class TopicPathParser implements Supplier<ImmutableTopicPath> {

        private final String topicPathString;
        private final LinkedList<String> topicPathParts;

        private TopicPathParser(final String topicPathString) {
            this.topicPathString = topicPathString;
            topicPathParts = splitByPathDelimiter(topicPathString);
        }

        private static LinkedList<String> splitByPathDelimiter(final String topicPathString) {
            final LinkedList<String> result =
                    StreamSupport.stream(newTopicOrPathPointer(topicPathString).spliterator(), false)
                            .map(JsonKey::toString)
                            .collect(Collectors.toCollection(LinkedList::new));

            if (topicPathString.startsWith(TopicPath.PATH_DELIMITER)) {
                // topic path starts with an empty segment
                result.addFirst("");
            }
            return result;
        }

        @Override
        public ImmutableTopicPath get() {
            final Builder topicPathBuilder = new Builder(tryToGetNamespace(), tryToGetEntityName());
            topicPathBuilder.group = tryToGetGroup(tryToGetGroupName());
            topicPathBuilder.channel = tryToGetChannelForGroup(topicPathBuilder.group);
            topicPathBuilder.criterion = tryToGetCriterion(tryToGetCriterionName());
            switch (topicPathBuilder.criterion) {
                case COMMANDS:
                case EVENTS:
                    topicPathBuilder.action = tryToGetActionForName(tryToGetActionName());
                    break;
                case SEARCH:
                    topicPathBuilder.searchAction = tryToGetSearchActionForName(tryToGetSearchActionName());
                    break;
                case STREAMING:
                    topicPathBuilder.streamingAction = tryToGetStreamingActionForName(tryToGetSearchActionName());
                    break;
                case ERRORS:
                    break;
                case MESSAGES:
                case ACKS:
                case ANNOUNCEMENTS:
                    topicPathBuilder.subject = getSubjectOrNull();
                    break;
                default:
                    throw UnknownTopicPathException.newBuilder(topicPathString)
                            .description(MessageFormat.format("Criterion <{0}> is unhandled.",
                                    topicPathBuilder.criterion))
                            .build();
            }
            return topicPathBuilder.build();
        }

        private String tryToGetNamespace() {
            try {
                return topicPathParts.pop(); // parts[0]
            } catch (final NoSuchElementException e) {
                throw UnknownTopicPathException.newBuilder(topicPathString)
                        .description("The topic path has no namespace part.")
                        .cause(e)
                        .build();
            }
        }

        private String tryToGetEntityName() {
            try {
                return topicPathParts.pop(); // parts[1]
            } catch (final NoSuchElementException e) {
                throw UnknownTopicPathException.newBuilder(topicPathString)
                        .description("The topic path has no entity name part.")
                        .cause(e)
                        .build();
            }
        }

        private String tryToGetGroupName() {
            try {
                return topicPathParts.pop(); // parts[2]
            } catch (final NoSuchElementException e) {
                throw UnknownTopicPathException.newBuilder(topicPathString)
                        .description("The topic path has no group part.")
                        .cause(e)
                        .build();
            }
        }

        private Group tryToGetGroup(final String groupName) {
            return Group.forName(groupName).orElseThrow(() -> UnknownTopicPathException.newBuilder(topicPathString)
                    .description(MessageFormat.format("Group name <{0}> is unknown.", groupName))
                    .build());
        }

        private Channel tryToGetChannelForGroup(final Group group) {
            final Channel result;
            if (Group.POLICIES == group || Group.CONNECTIONS == group) {
                result = Channel.NONE;
            } else {
                result = tryToGetChannelForName(tryToGetChannelName());
            }
            return result;
        }

        private String tryToGetChannelName() {
            try {
                return topicPathParts.pop(); // parts[3]
            } catch (final NoSuchElementException e) {
                throw UnknownTopicPathException.newBuilder(topicPathString)
                        .description("The topic path has no channel part.")
                        .cause(e)
                        .build();
            }
        }

        private Channel tryToGetChannelForName(final String channelName) {
            return Channel.forName(channelName).orElseThrow(() -> UnknownTopicPathException.newBuilder(topicPathString)
                    .description(MessageFormat.format("Channel name <{0}> is unknown.", channelName))
                    .build());
        }

        private String tryToGetCriterionName() {
            try {
                return topicPathParts.pop(); // parts[4]
            } catch (final NoSuchElementException e) {
                throw UnknownTopicPathException.newBuilder(topicPathString)
                        .description("The topic path has no criterion part.")
                        .cause(e)
                        .build();
            }
        }

        private Criterion tryToGetCriterion(final String criterionName) {
            return Criterion.forName(criterionName)
                    .orElseThrow(() -> UnknownTopicPathException.newBuilder(topicPathString)
                            .description(MessageFormat.format("Criterion name <{0}> is unknown.", criterionName))
                            .build());
        }

        private String tryToGetActionName() {
            try {
                return topicPathParts.pop(); // parts[5]
            } catch (final NoSuchElementException e) {
                throw UnknownTopicPathException.newBuilder(topicPathString)
                        .description("The topic path has no action part.")
                        .cause(e)
                        .build();
            }
        }

        private Action tryToGetActionForName(final String actionName) {
            return Action.forName(actionName).orElseThrow(() -> UnknownTopicPathException.newBuilder(topicPathString)
                    .description(MessageFormat.format("Action name <{0}> is unknown.", actionName))
                    .build());
        }

        private String tryToGetSearchActionName() {
            try {
                return topicPathParts.pop(); // parts[5]
            } catch (final NoSuchElementException e) {
                throw UnknownTopicPathException.newBuilder(topicPathString)
                        .description("The topic path has no search action part.")
                        .cause(e)
                        .build();
            }
        }

        private SearchAction tryToGetSearchActionForName(final String searchActionName) {
            return SearchAction.forName(searchActionName)
                    .orElseThrow(() -> UnknownTopicPathException.newBuilder(topicPathString)
                            .description(MessageFormat.format("Search action name <{0}> is unknown.", searchActionName))
                            .build());
        }

        private StreamingAction tryToGetStreamingActionForName(final String streamingActionName) {
            return StreamingAction.forName(streamingActionName)
                    .orElseThrow(() -> UnknownTopicPathException.newBuilder(topicPathString)
                            .description(MessageFormat.format("Streaming action name <{0}> is unknown.", streamingActionName))
                            .build());
        }

        @Nullable
        private String getSubjectOrNull() {
            final String subject = String.join(TopicPath.PATH_DELIMITER, topicPathParts);
            final String result;
            if (subject.isEmpty()) {
                result = null;
            } else {
                result = subject;
            }
            return result;
        }

    }

}
