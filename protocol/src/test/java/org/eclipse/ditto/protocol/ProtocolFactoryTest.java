/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.base.model.entity.id.AbstractNamespacedEntityId;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.protocol.adapter.UnknownTopicPathException;
import org.junit.Test;

/**
 * Tests {@link ProtocolFactory}.
 */
public class ProtocolFactoryTest {

    private static final String NAMESPACE = "namespace";
    private static final String NAME = "name";

    @Test(expected = UnknownTopicPathException.class)
    public void testInvalidPolicyModifyTopicPathWithChannel() {
        ProtocolFactory.newTopicPath("namespace/name/policies/twin/commands/modify");
    }

    @Test(expected = UnknownTopicPathException.class)
    public void testInvalidGroup() {
        ProtocolFactory.newTopicPath("namespace/name/invalid/commands/modify");
    }

    @Test(expected = UnknownTopicPathException.class)
    public void testInvalidCriterion() {
        ProtocolFactory.newTopicPath("namespace/name/things/twin/invalid/modify");
    }

    @Test(expected = UnknownTopicPathException.class)
    public void testInvalidChannel() {
        ProtocolFactory.newTopicPath("namespace/name/things/invalid/commands/modify");
    }

    @Test(expected = UnknownTopicPathException.class)
    public void testInvalidAction() {
        ProtocolFactory.newTopicPath("namespace/name/things/twin/commands/invalid");
    }

    @Test(expected = UnknownTopicPathException.class)
    public void testMissingGroup() {
        ProtocolFactory.newTopicPath("namespace/name");
    }

    @Test(expected = UnknownTopicPathException.class)
    public void testMissingId() {
        ProtocolFactory.newTopicPath("namespace");
    }

    @Test(expected = UnknownTopicPathException.class)
    public void testMissingNamespace() {
        ProtocolFactory.newTopicPath("");
    }

    @Test(expected = NullPointerException.class)
    public void testNullPath() {
        ProtocolFactory.newTopicPath(null);
    }

    @Test
    public void testNewTopicPathBuilderFromThingId() {
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(ThingId.of(NAMESPACE, NAME));
        final TopicPath topicPath = topicPathBuilder.twin().commands().modify().build();
        assertThat(topicPath.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(topicPath.getEntityName()).isEqualTo(NAME);
        assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.THINGS);
        assertThat(topicPath.getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        assertThat(topicPath.getAction()).contains(TopicPath.Action.MODIFY);
    }

    @Test
    public void testNewTopicPathBuilderFromNamespacedEntityId() {
        final TopicPathBuilder topicPathBuilder =
                ProtocolFactory.newTopicPathBuilder(
                        new AbstractNamespacedEntityId(ThingConstants.ENTITY_TYPE, NAMESPACE, NAME, true) {});
        final TopicPath topicPath = topicPathBuilder.twin().commands().modify().build();
        assertThat(topicPath.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(topicPath.getEntityName()).isEqualTo(NAME);
        assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.THINGS);
        assertThat(topicPath.getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        assertThat(topicPath.getAction()).contains(TopicPath.Action.MODIFY);
    }

    @Test
    public void testNewTopicPathBuilderFromPolicyId() {
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(PolicyId.of(NAMESPACE, NAME));
        final TopicPath topicPath = topicPathBuilder.none().commands().modify().build();
        assertThat(topicPath.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(topicPath.getEntityName()).isEqualTo(NAME);
        assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.NONE);
        assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.POLICIES);
        assertThat(topicPath.getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        assertThat(topicPath.getAction()).contains(TopicPath.Action.MODIFY);
    }

    @Test
    public void testNewTopicPathBuilderFromNamespace() {
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilderFromNamespace(NAMESPACE);
        final TopicPath topicPath = topicPathBuilder.twin().commands().modify().build();
        assertThat(topicPath.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(topicPath.getEntityName()).isEqualTo(TopicPath.ID_PLACEHOLDER);
        assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.THINGS);
        assertThat(topicPath.getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        assertThat(topicPath.getAction()).contains(TopicPath.Action.MODIFY);
    }

    @Test
    public void testNewTopicPathBuilderFromName() {
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilderFromName(NAME);
        final TopicPath topicPath = topicPathBuilder.things().twin().commands().modify().build();
        assertThat(topicPath.getNamespace()).isEqualTo(TopicPath.ID_PLACEHOLDER);
        assertThat(topicPath.getEntityName()).isEqualTo(NAME);
        assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.THINGS);
        assertThat(topicPath.getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        assertThat(topicPath.getAction()).contains(TopicPath.Action.MODIFY);
    }

}
