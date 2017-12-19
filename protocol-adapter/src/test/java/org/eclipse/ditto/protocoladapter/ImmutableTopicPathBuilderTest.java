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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 */
public class ImmutableTopicPathBuilderTest {

    /** */
    @Test
    public void buildThingModifyCommandTopicPath() {
        final TopicPath expected = ImmutableTopicPath
                .of("org.eclipse.ditto.test", "myThing", TopicPath.Group.THINGS, TopicPath.Channel.TWIN,
                        TopicPath.Criterion.COMMANDS, TopicPath.Action.MODIFY);
        final TopicPath actual = ProtocolFactory.newTopicPathBuilder("org.eclipse.ditto.test:myThing") //
                .twin() //
                .commands() //
                .modify() //
                .build();

        final String expectedTopicPathString = "org.eclipse.ditto.test/myThing/things/twin/commands/modify";

        assertThat(actual).isEqualTo(expected);
        assertThat(actual.getPath()).isEqualTo(expectedTopicPathString);
    }

    /** */
    @Test
    public void buildThingModifiedEventTopicPath() {
        final TopicPath expected = ImmutableTopicPath
                .of("org.eclipse.ditto.test", "myThing", TopicPath.Group.THINGS, TopicPath.Channel.TWIN,
                        TopicPath.Criterion.EVENTS, TopicPath.Action.MODIFIED);
        final TopicPath actual = ProtocolFactory.newTopicPathBuilder("org.eclipse.ditto.test:myThing") //
                .twin() //
                .events() //
                .modified() //
                .build();

        final String expectedTopicPathString = "org.eclipse.ditto.test/myThing/things/twin/events/modified";

        assertThat(actual).isEqualTo(expected);
        assertThat(actual.getPath()).isEqualTo(expectedTopicPathString);
    }

}
