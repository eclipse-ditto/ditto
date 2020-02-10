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
package org.eclipse.ditto.protocoladapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.model.base.entity.id.DefaultNamespacedEntityId;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.ThingId;
import org.junit.Test;

/**
 * Tests ProtocolFactory.
 */
public class ProtocolFactoryTest {

    private static final String NAMESPACE = "namespace";
    private static final String ID = "id";

    @Test(expected = UnknownTopicPathException.class)
    public void testInvalidPolicyModifyTopicPathWithChannel() {
        ProtocolFactory.newTopicPath("namespace/id/policies/twin/commands/modify");
    }

    @Test(expected = UnknownTopicPathException.class)
    public void testInvalidGroup() {
        ProtocolFactory.newTopicPath("namespace/id/invalid/commands/modify");
    }

    @Test(expected = UnknownTopicPathException.class)
    public void testInvalidCriterion() {
        ProtocolFactory.newTopicPath("namespace/id/things/twin/invalid/modify");
    }

    @Test(expected = UnknownTopicPathException.class)
    public void testInvalidChannel() {
        ProtocolFactory.newTopicPath("namespace/id/things/invalid/commands/modify");
    }

    @Test(expected = UnknownTopicPathException.class)
    public void testInvalidAction() {
        ProtocolFactory.newTopicPath("namespace/id/things/twin/commands/invalid");
    }

    @Test(expected = UnknownTopicPathException.class)
    public void testMissingGroup() {
        ProtocolFactory.newTopicPath("namespace/id");
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
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(ThingId.of(NAMESPACE, ID));
        final TopicPath topicPath = topicPathBuilder.twin().commands().modify().build();
        assertThat(topicPath.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(topicPath.getId()).isEqualTo(ID);
        assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.THINGS);
    }

    @Test
    public void testNewTopicPathBuilderFromNamespacedEntityId() {
        final TopicPathBuilder topicPathBuilder =
                ProtocolFactory.newTopicPathBuilder(DefaultNamespacedEntityId.of(NAMESPACE, ID));
        final TopicPath topicPath = topicPathBuilder.twin().commands().modify().build();
        assertThat(topicPath.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(topicPath.getId()).isEqualTo(ID);
        assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.THINGS);
    }

    @Test
    public void testNewTopicPathBuilderFromPolicyId() {
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(PolicyId.of(NAMESPACE, ID));
        final TopicPath topicPath = topicPathBuilder.none().commands().modify().build();
        assertThat(topicPath.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(topicPath.getId()).isEqualTo(ID);
        assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.NONE);
        assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.POLICIES);
    }

    @Test
    public void testNewTopicPathBuilderFromNamespace() {
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilderFromNamespace(NAMESPACE);
        final TopicPath topicPath = topicPathBuilder.twin().commands().modify().build();
        assertThat(topicPath.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(topicPath.getId()).isEqualTo(TopicPath.ID_PLACEHOLDER);
        assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.THINGS);
    }
}