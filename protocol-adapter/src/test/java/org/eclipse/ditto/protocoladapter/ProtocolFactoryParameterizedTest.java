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
import static org.eclipse.ditto.protocoladapter.ImmutableTopicPath.of;
import static org.eclipse.ditto.protocoladapter.TopicPath.Action.CREATE;
import static org.eclipse.ditto.protocoladapter.TopicPath.Action.CREATED;
import static org.eclipse.ditto.protocoladapter.TopicPath.Action.DELETE;
import static org.eclipse.ditto.protocoladapter.TopicPath.Action.DELETED;
import static org.eclipse.ditto.protocoladapter.TopicPath.Action.MODIFIED;
import static org.eclipse.ditto.protocoladapter.TopicPath.Action.MODIFY;
import static org.eclipse.ditto.protocoladapter.TopicPath.Action.RETRIEVE;
import static org.eclipse.ditto.protocoladapter.TopicPath.Channel.LIVE;
import static org.eclipse.ditto.protocoladapter.TopicPath.Channel.NONE;
import static org.eclipse.ditto.protocoladapter.TopicPath.Channel.TWIN;
import static org.eclipse.ditto.protocoladapter.TopicPath.Criterion.COMMANDS;
import static org.eclipse.ditto.protocoladapter.TopicPath.Criterion.ERRORS;
import static org.eclipse.ditto.protocoladapter.TopicPath.Criterion.EVENTS;
import static org.eclipse.ditto.protocoladapter.TopicPath.Criterion.MESSAGES;
import static org.eclipse.ditto.protocoladapter.TopicPath.Group.POLICIES;
import static org.eclipse.ditto.protocoladapter.TopicPath.Group.THINGS;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ProtocolFactoryParameterizedTest {

    private static final String ns = "ns";
    private static final String ID = "id";

    @Parameterized.Parameters(name = "path: {0}")
    public static Collection<Object[]> data() {
        final Object[][] data = {
                {"ns/id/things/twin/commands/modify", of(ns, ID, THINGS, TWIN, COMMANDS, MODIFY)},
                {"ns/id/things/twin/commands/create", of(ns, ID, THINGS, TWIN, COMMANDS, CREATE)},
                {"ns/id/things/twin/commands/delete", of(ns, ID, THINGS, TWIN, COMMANDS, DELETE)},
                {"ns/id/things/twin/commands/retrieve", of(ns, ID, THINGS, TWIN, COMMANDS, RETRIEVE)},
                {"ns/id/things/twin/events/created", of(ns, ID, THINGS, TWIN, EVENTS, CREATED)},
                {"ns/id/things/twin/events/modified", of(ns, ID, THINGS, TWIN, EVENTS, MODIFIED)},
                {"ns/id/things/twin/events/deleted", of(ns, ID, THINGS, TWIN, EVENTS, DELETED)},
                {"ns/id/things/live/events/created", of(ns, ID, THINGS, LIVE, EVENTS, CREATED)},
                {"ns/id/things/live/events/modified", of(ns, ID, THINGS, LIVE, EVENTS, MODIFIED)},
                {"ns/id/things/live/events/deleted", of(ns, ID, THINGS, LIVE, EVENTS, DELETED)},
                {"ns/id/things/twin/errors", of(ns, ID, THINGS, TWIN, ERRORS)},
                {"ns/id/things/live/messages/subject/with/multiple/slashes",
                        of(ns, ID, THINGS, LIVE, MESSAGES, "subject/with/multiple/slashes")},
                {"ns/id/policies/commands/modify", of(ns, ID, POLICIES, NONE, COMMANDS, MODIFY)},
                {"ns/id/policies/commands/retrieve", of(ns, ID, POLICIES, NONE, COMMANDS, RETRIEVE)},
                {"ns/id/policies/commands/create", of(ns, ID, POLICIES, NONE, COMMANDS, CREATE)},
                {"ns/id/policies/commands/delete", of(ns, ID, POLICIES, NONE, COMMANDS, DELETE)},
                {"ns/id/policies/errors", of(ns, ID, POLICIES, NONE, ERRORS)}
        };
        return Arrays.asList(data);
    }

    @Parameterized.Parameter
    public String input;

    @Parameterized.Parameter(1)
    public TopicPath expected;

    @Test
    public void test() {
        final TopicPath actual = ProtocolFactory.newTopicPath(input);
        assertThat(actual).isEqualTo(expected);
    }
}
