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

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.entity.id.AbstractNamespacedEntityId;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.protocol.adapter.UnknownTopicPathException;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit tests for {@link ProtocolFactory}.
 */
public final class ProtocolFactoryTest {

    private static final String NAMESPACE = "namespace";
    private static final String NAME = "name";

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test(expected = UnknownTopicPathException.class)
    public void testInvalidPolicyModifyTopicPathWithChannel() {
        ProtocolFactory.newTopicPath("namespace/name/policies/twin/commands/modify");
    }

    @Test
    public void testNewTopicPathBuilderFromThingId() {
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(ThingId.of(NAMESPACE, NAME));
        final TopicPath topicPath = topicPathBuilder.twin().commands().modify().build();

        softly.assertThat(topicPath.getNamespace()).isEqualTo(NAMESPACE);
        softly.assertThat(topicPath.getEntityName()).isEqualTo(NAME);
        softly.assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        softly.assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.THINGS);
        softly.assertThat(topicPath.getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        softly.assertThat(topicPath.getAction()).contains(TopicPath.Action.MODIFY);
    }

    @Test
    public void testNewTopicPathBuilderFromNamespacedEntityId() {
        final TopicPathBuilder topicPathBuilder =
                ProtocolFactory.newTopicPathBuilder(
                        new AbstractNamespacedEntityId(ThingConstants.ENTITY_TYPE, NAMESPACE, NAME, true) {});
        final TopicPath topicPath = topicPathBuilder.twin().commands().modify().build();

        softly.assertThat(topicPath.getNamespace()).isEqualTo(NAMESPACE);
        softly.assertThat(topicPath.getEntityName()).isEqualTo(NAME);
        softly.assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        softly.assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.THINGS);
        softly.assertThat(topicPath.getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        softly.assertThat(topicPath.getAction()).contains(TopicPath.Action.MODIFY);
    }

    @Test
    public void testNewTopicPathBuilderFromPolicyId() {
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(PolicyId.of(NAMESPACE, NAME));
        final TopicPath topicPath = topicPathBuilder.none().commands().modify().build();

        softly.assertThat(topicPath.getNamespace()).isEqualTo(NAMESPACE);
        softly.assertThat(topicPath.getEntityName()).isEqualTo(NAME);
        softly.assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.NONE);
        softly.assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.POLICIES);
        softly.assertThat(topicPath.getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        softly.assertThat(topicPath.getAction()).contains(TopicPath.Action.MODIFY);
    }

    @Test
    public void testNewTopicPathBuilderFromNamespace() {
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilderFromNamespace(NAMESPACE);
        final TopicPath topicPath = topicPathBuilder.twin().commands().modify().build();

        softly.assertThat(topicPath.getNamespace()).isEqualTo(NAMESPACE);
        softly.assertThat(topicPath.getEntityName()).isEqualTo(TopicPath.ID_PLACEHOLDER);
        softly.assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        softly.assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.THINGS);
        softly.assertThat(topicPath.getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        softly.assertThat(topicPath.getAction()).contains(TopicPath.Action.MODIFY);
    }

    @Test
    public void testNewTopicPathBuilderFromName() {
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilderFromName(NAME);
        final TopicPath topicPath = topicPathBuilder.things().twin().commands().modify().build();
        softly.assertThat(topicPath.getNamespace()).isEqualTo(TopicPath.ID_PLACEHOLDER);
        softly.assertThat(topicPath.getEntityName()).isEqualTo(NAME);
        softly.assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        softly.assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.THINGS);
        softly.assertThat(topicPath.getCriterion()).isEqualTo(TopicPath.Criterion.COMMANDS);
        softly.assertThat(topicPath.getAction()).contains(TopicPath.Action.MODIFY);
    }

}
