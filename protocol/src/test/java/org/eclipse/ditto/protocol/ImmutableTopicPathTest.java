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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.protocol.adapter.UnknownTopicPathException;
import org.junit.Rule;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableTopicPath}.
 */
public final class ImmutableTopicPathTest {

    private static final String NAMESPACE = "org.eclipse.ditto.test";
    private static final String ENTITY_NAME = "myThing";

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableTopicPath.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableTopicPath.class, areImmutable(), provided(TopicPath.Group.class).isAlsoImmutable());
    }

    @Test
    public void newBuilderWithNullNamespace() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableTopicPath.newBuilder(null, ENTITY_NAME))
                .withMessage("The namespace must not be null!")
                .withNoCause();
    }

    @Test
    public void newBuilderWithNullEntityId() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableTopicPath.newBuilder(NAMESPACE, null))
                .withMessage("The entityName must not be null!")
                .withNoCause();
    }

    @Test
    public void buildThingModifyCommandTopicPath() {
        final TopicPath underTest =
                ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).things().twin().commands().modify().build();

        softly.assertThat(underTest.getNamespace()).as("namespace").isEqualTo(NAMESPACE);
        softly.assertThat(underTest.getEntityName()).as("entity name").isEqualTo(ENTITY_NAME);
        softly.assertThat(underTest.getGroup()).as("group").isEqualTo(TopicPath.Group.THINGS);
        softly.assertThat(underTest.getChannel()).as("channel").isEqualTo(TopicPath.Channel.TWIN);
        softly.assertThat(underTest.getCriterion()).as("criterion").isEqualTo(TopicPath.Criterion.COMMANDS);
        softly.assertThat(underTest.getAction()).as("action").hasValue(TopicPath.Action.MODIFY);
        softly.assertThat(underTest.getSearchAction()).as("search action").isEmpty();
        softly.assertThat(underTest.getSubject()).as("subject").isEmpty();
        softly.assertThat(underTest.getPath()).as("path")
                .isEqualTo(String.join(TopicPath.PATH_DELIMITER,
                        NAMESPACE,
                        ENTITY_NAME,
                        TopicPath.Group.THINGS.getName(),
                        TopicPath.Channel.TWIN.getName(),
                        TopicPath.Criterion.COMMANDS.getName(),
                        TopicPath.Action.MODIFY.getName()));
    }

    @Test
    public void buildThingModifiedEventTopicPath() {
        final TopicPath underTest =
                ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).things().twin().events().modified().build();

        softly.assertThat(underTest.getNamespace()).as("namespace").isEqualTo(NAMESPACE);
        softly.assertThat(underTest.getEntityName()).as("entity name").isEqualTo(ENTITY_NAME);
        softly.assertThat(underTest.getGroup()).as("group").isEqualTo(TopicPath.Group.THINGS);
        softly.assertThat(underTest.getChannel()).as("channel").isEqualTo(TopicPath.Channel.TWIN);
        softly.assertThat(underTest.getCriterion()).as("criterion").isEqualTo(TopicPath.Criterion.EVENTS);
        softly.assertThat(underTest.getAction()).as("action").hasValue(TopicPath.Action.MODIFIED);
        softly.assertThat(underTest.getSearchAction()).as("search action").isEmpty();
        softly.assertThat(underTest.getSubject()).as("subject").isEmpty();
        softly.assertThat(underTest.getPath()).as("path")
                .isEqualTo(String.join(TopicPath.PATH_DELIMITER,
                        NAMESPACE,
                        ENTITY_NAME,
                        TopicPath.Group.THINGS.getName(),
                        TopicPath.Channel.TWIN.getName(),
                        TopicPath.Criterion.EVENTS.getName(),
                        TopicPath.Action.MODIFIED.getName()));
    }

    @Test
    public void buildLiveMessageWithSubjectTopicPath() {
        final String subject = "subject/with/multiple/slashes";
        final TopicPath underTest = ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                .things()
                .live()
                .messages()
                .subject(subject)
                .build();

        softly.assertThat(underTest.getNamespace()).as("namespace").isEqualTo(NAMESPACE);
        softly.assertThat(underTest.getEntityName()).as("entity name").isEqualTo(ENTITY_NAME);
        softly.assertThat(underTest.getGroup()).as("group").isEqualTo(TopicPath.Group.THINGS);
        softly.assertThat(underTest.getChannel()).as("channel").isEqualTo(TopicPath.Channel.LIVE);
        softly.assertThat(underTest.getCriterion()).as("criterion").isEqualTo(TopicPath.Criterion.MESSAGES);
        softly.assertThat(underTest.getAction()).as("action").isEmpty();
        softly.assertThat(underTest.getSearchAction()).as("search action").isEmpty();
        softly.assertThat(underTest.getSubject()).as("subject").hasValue(subject);
        softly.assertThat(underTest.getPath()).as("path")
                .isEqualTo(String.join(TopicPath.PATH_DELIMITER,
                        NAMESPACE,
                        ENTITY_NAME,
                        TopicPath.Group.THINGS.getName(),
                        TopicPath.Channel.LIVE.getName(),
                        TopicPath.Criterion.MESSAGES.getName(),
                        subject));
    }

    @Test
    public void buildPolicyModifyCommandTopicPath() {
        final TopicPath underTest =
                ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).policies().commands().modify().build();

        softly.assertThat(underTest.getNamespace()).as("namespace").isEqualTo(NAMESPACE);
        softly.assertThat(underTest.getEntityName()).as("entity name").isEqualTo(ENTITY_NAME);
        softly.assertThat(underTest.getGroup()).as("group").isEqualTo(TopicPath.Group.POLICIES);
        softly.assertThat(underTest.getChannel()).as("channel").isEqualTo(TopicPath.Channel.NONE);
        softly.assertThat(underTest.getCriterion()).as("criterion").isEqualTo(TopicPath.Criterion.COMMANDS);
        softly.assertThat(underTest.getAction()).as("action").hasValue(TopicPath.Action.MODIFY);
        softly.assertThat(underTest.getSearchAction()).as("search action").isEmpty();
        softly.assertThat(underTest.getSubject()).as("subject").isEmpty();
        softly.assertThat(underTest.getPath()).as("path")
                .isEqualTo(String.join(TopicPath.PATH_DELIMITER,
                        NAMESPACE,
                        ENTITY_NAME,
                        TopicPath.Group.POLICIES.getName(),
                        TopicPath.Criterion.COMMANDS.getName(),
                        TopicPath.Action.MODIFY.getName()));
    }

    @Test
    public void buildPolicyModifyCommandTopicPathWitNoneChannel() {
        final TopicPath underTest =
                ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).policies().none().commands().modify().build();

        softly.assertThat(underTest.getNamespace()).as("namespace").isEqualTo(NAMESPACE);
        softly.assertThat(underTest.getEntityName()).as("entity name").isEqualTo(ENTITY_NAME);
        softly.assertThat(underTest.getGroup()).as("group").isEqualTo(TopicPath.Group.POLICIES);
        softly.assertThat(underTest.getChannel()).as("channel").isEqualTo(TopicPath.Channel.NONE);
        softly.assertThat(underTest.getCriterion()).as("criterion").isEqualTo(TopicPath.Criterion.COMMANDS);
        softly.assertThat(underTest.getAction()).as("action").hasValue(TopicPath.Action.MODIFY);
        softly.assertThat(underTest.getSearchAction()).as("search action").isEmpty();
        softly.assertThat(underTest.getSubject()).as("subject").isEmpty();
        softly.assertThat(underTest.getPath()).as("path")
                .isEqualTo(String.join(TopicPath.PATH_DELIMITER,
                        NAMESPACE,
                        ENTITY_NAME,
                        TopicPath.Group.POLICIES.getName(),
                        TopicPath.Criterion.COMMANDS.getName(),
                        TopicPath.Action.MODIFY.getName()));
    }

    @Test
    public void buildThingSearchCommandTopicPath() {
        final String namespace = "_";
        final String entityName = "_";
        final TopicPath underTest =
                ImmutableTopicPath.newBuilder(namespace, entityName).things().twin().search().subscribe().build();

        softly.assertThat(underTest.getNamespace()).as("namespace").isEqualTo(namespace);
        softly.assertThat(underTest.getEntityName()).as("entity name").isEqualTo(entityName);
        softly.assertThat(underTest.getGroup()).as("group").isEqualTo(TopicPath.Group.THINGS);
        softly.assertThat(underTest.getChannel()).as("channel").isEqualTo(TopicPath.Channel.TWIN);
        softly.assertThat(underTest.getCriterion()).as("criterion").isEqualTo(TopicPath.Criterion.SEARCH);
        softly.assertThat(underTest.getAction()).as("action").isEmpty();
        softly.assertThat(underTest.getSearchAction()).as("search action").hasValue(TopicPath.SearchAction.SUBSCRIBE);
        softly.assertThat(underTest.getSubject()).as("subject").isEmpty();
        softly.assertThat(underTest.getPath()).as("path")
                .isEqualTo(String.join(TopicPath.PATH_DELIMITER,
                        namespace,
                        entityName,
                        TopicPath.Group.THINGS.getName(),
                        TopicPath.Channel.TWIN.getName(),
                        TopicPath.Criterion.SEARCH.getName(),
                        TopicPath.SearchAction.SUBSCRIBE.getName()));
    }

    @Test
    public void buildThingErrorTopicPath() {
        final TopicPath underTest =
                ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).things().twin().errors().build();

        softly.assertThat(underTest.getNamespace()).as("namespace").isEqualTo(NAMESPACE);
        softly.assertThat(underTest.getEntityName()).as("entity name").isEqualTo(ENTITY_NAME);
        softly.assertThat(underTest.getGroup()).as("group").isEqualTo(TopicPath.Group.THINGS);
        softly.assertThat(underTest.getChannel()).as("channel").isEqualTo(TopicPath.Channel.TWIN);
        softly.assertThat(underTest.getCriterion()).as("criterion").isEqualTo(TopicPath.Criterion.ERRORS);
        softly.assertThat(underTest.getAction()).as("action").isEmpty();
        softly.assertThat(underTest.getSearchAction()).as("search action").isEmpty();
        softly.assertThat(underTest.getSubject()).as("subject").isEmpty();
        softly.assertThat(underTest.getPath()).as("path")
                .isEqualTo(String.join(TopicPath.PATH_DELIMITER,
                        NAMESPACE,
                        ENTITY_NAME,
                        TopicPath.Group.THINGS.getName(),
                        TopicPath.Channel.TWIN.getName(),
                        TopicPath.Criterion.ERRORS.getName()));
    }

    @Test
    public void buildConnectivityAnnouncementTopicPath() {
        final String namespace = "_";
        final String connectionId = "myConnection";
        final String announcement = "opened";

        final TopicPath underTest = ImmutableTopicPath.newBuilder(namespace, connectionId)
                .connections().announcements().name(announcement).build();

        softly.assertThat(underTest.getNamespace()).as("namespace").isEqualTo(namespace);
        softly.assertThat(underTest.getEntityName()).as("entity name").isEqualTo(connectionId);
        softly.assertThat(underTest.getGroup()).as("group").isEqualTo(TopicPath.Group.CONNECTIONS);
        softly.assertThat(underTest.getChannel()).as("channel").isEqualTo(TopicPath.Channel.NONE);
        softly.assertThat(underTest.getCriterion()).as("criterion").isEqualTo(TopicPath.Criterion.ANNOUNCEMENTS);
        softly.assertThat(underTest.getAction()).as("action").isEmpty();
        softly.assertThat(underTest.getSearchAction()).as("search action").isEmpty();
        softly.assertThat(underTest.getSubject()).as("subject").contains(announcement);
        softly.assertThat(underTest.getPath()).as("path")
                .isEqualTo(String.join(TopicPath.PATH_DELIMITER,
                        namespace,
                        connectionId,
                        TopicPath.Group.CONNECTIONS.getName(),
                        TopicPath.Criterion.ANNOUNCEMENTS.getName(),
                        announcement));
    }

    @Test
    public void buildTopicPathWithPoliciesGroupAndLiveChannelThrowsException() {
        assertThatIllegalStateException()
                .isThrownBy(() -> ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .policies()
                        .live()
                        .announcements()
                        .name("something-happened")
                        .build())
                .withNoCause();
    }

    @Test
    public void buildTopicPathWithConnectionsGroupAndLiveChannelThrowsException() {
        assertThatIllegalStateException()
                .isThrownBy(() -> ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .connections()
                        .live()
                        .announcements()
                        .name("opened")
                        .build())
                .withNoCause();
    }

    @Test
    public void getPathWithEmptyNamespaceReturnsExpected() {
        final TopicPath underTest =
                ImmutableTopicPath.newBuilder("", ENTITY_NAME).things().live().commands().retrieve().build();

        assertThat(underTest.getPath()).isEqualTo("/" + ENTITY_NAME + "/things/live/commands/retrieve");
    }

    @Test
    public void isGroupReturnsExpected() {
        final TopicPath underTest =
                ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).things().twin().messages().build();

        softly.assertThat(underTest.isGroup(TopicPath.Group.THINGS)).as("actual group").isTrue();
        softly.assertThat(underTest.isGroup(null)).as("null group").isFalse();
        softly.assertThat(underTest.isGroup(TopicPath.Group.POLICIES)).as("different group").isFalse();
    }

    @Test
    public void isChannelReturnsExpected() {
        final TopicPath underTest =
                ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).things().twin().commands().modify().build();
        final TopicPath.Channel actualChannel = TopicPath.Channel.TWIN;

        softly.assertThat(underTest.isChannel(actualChannel)).as("actual channel").isTrue();
        softly.assertThat(underTest.isChannel(null)).as("null channel").isFalse();
        Stream.of(TopicPath.Channel.values())
                .filter(c -> !c.equals(actualChannel))
                .forEach(c -> softly.assertThat(underTest.isChannel(c)).as(c.getName()).isFalse());
    }

    @Test
    public void isCriterionReturnsExpected() {
        final TopicPath underTest =
                ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).things().commands().modify().build();
        final TopicPath.Criterion actualCriterion = TopicPath.Criterion.COMMANDS;

        softly.assertThat(underTest.isCriterion(actualCriterion)).as("actual criterion").isTrue();
        softly.assertThat(underTest.isCriterion(null)).as("null criterion").isFalse();
        Stream.of(TopicPath.Criterion.values())
                .filter(c -> !c.equals(actualCriterion))
                .forEach(c -> softly.assertThat(underTest.isCriterion(c)).as(c.getName()).isFalse());
    }

    @Test
    public void isActionReturnsExpected() {
        final TopicPath underTestWithAction =
                ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).policies().commands().retrieve().build();
        final TopicPath.Action actualAction = TopicPath.Action.RETRIEVE;

        softly.assertThat(underTestWithAction.isAction(actualAction)).as("actual action").isTrue();
        softly.assertThat(underTestWithAction.isAction(null)).as("null action").isFalse();
        Stream.of(TopicPath.Action.values())
                .filter(a -> !a.equals(actualAction))
                .forEach(a -> softly.assertThat(underTestWithAction.isAction(a)).as(a.getName()).isFalse());

        final TopicPath underTestWithoutAction =
                ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).things().twin().search().request().build();

        softly.assertThat(underTestWithoutAction.isAction(null)).as("null action").isTrue();
    }

    @Test
    public void parseNullStringThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableTopicPath.parseTopicPath(null))
                .withMessage("The topicPathString must not be null!")
                .withNoCause();
    }

    @Test
    public void parseEmptyStringFailsBecauseOfMissingNamespace() {
        assertThatExceptionOfType(UnknownTopicPathException.class)
                .isThrownBy(() -> ProtocolFactory.newTopicPath(""))
                .satisfies(exception -> assertThat(exception.getDescription()).hasValue(
                        "The topic path has no namespace part."))
                .withCauseInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void parseStringWithMissingEntityName() {
        assertThatExceptionOfType(UnknownTopicPathException.class)
                .isThrownBy(() -> ProtocolFactory.newTopicPath(NAMESPACE))
                .satisfies(exception -> assertThat(exception.getDescription()).hasValue(
                        "The topic path has no entity name part."))
                .withCauseInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void parseStringWithMissingGroup() {
        final String topicPathString = String.join(TopicPath.PATH_DELIMITER, NAMESPACE, ENTITY_NAME);

        assertThatExceptionOfType(UnknownTopicPathException.class)
                .isThrownBy(() -> ProtocolFactory.newTopicPath(topicPathString))
                .satisfies(exception -> assertThat(exception.getDescription()).hasValue(
                        "The topic path has no group part."))
                .withCauseInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void parseStringWithInvalidGroup() {
        final String groupName = "invalid";
        final String topicPathString = String.join(TopicPath.PATH_DELIMITER,
                NAMESPACE,
                ENTITY_NAME,
                groupName,
                "twin",
                "commands",
                "modify");

        assertThatExceptionOfType(UnknownTopicPathException.class)
                .isThrownBy(() -> ProtocolFactory.newTopicPath(topicPathString))
                .satisfies(exception -> assertThat(exception.getDescription()).hasValue(String.format(
                        "Group name <%s> is unknown.",
                        groupName)))
                .withNoCause();
    }

    @Test
    public void parseStringWithInvalidCriterion() {
        final String criterionName = "invalid";
        final String topicPathString = String.join(TopicPath.PATH_DELIMITER,
                NAMESPACE,
                ENTITY_NAME,
                "things",
                "twin",
                criterionName,
                "modify");

        assertThatExceptionOfType(UnknownTopicPathException.class)
                .isThrownBy(() -> ProtocolFactory.newTopicPath(topicPathString))
                .satisfies(exception -> assertThat(exception.getDescription()).hasValue(String.format(
                        "Criterion name <%s> is unknown.",
                        criterionName)))
                .withNoCause();
    }

    @Test
    public void parseStringWithInvalidChannel() {
        final String channelName = "invalid";
        final String topicPathString = String.join(TopicPath.PATH_DELIMITER,
                NAMESPACE,
                ENTITY_NAME,
                "things",
                channelName,
                "commands",
                "modify");

        assertThatExceptionOfType(UnknownTopicPathException.class)
                .isThrownBy(() -> ProtocolFactory.newTopicPath(topicPathString))
                .satisfies(exception -> assertThat(exception.getDescription()).hasValue(String.format(
                        "Channel name <%s> is unknown.",
                        channelName)))
                .withNoCause();
    }

    @Test
    public void parseStringWithInvalidAction() {
        final String actionName = "invalid";
        final String topicPathString =
                String.join(TopicPath.PATH_DELIMITER, NAMESPACE, ENTITY_NAME, "things", "twin", "commands", actionName);

        assertThatExceptionOfType(UnknownTopicPathException.class)
                .isThrownBy(() -> ProtocolFactory.newTopicPath(topicPathString))
                .satisfies(exception -> assertThat(exception.getDescription()).hasValue(String.format(
                        "Action name <%s> is unknown.",
                        actionName)))
                .withNoCause();
    }

}
