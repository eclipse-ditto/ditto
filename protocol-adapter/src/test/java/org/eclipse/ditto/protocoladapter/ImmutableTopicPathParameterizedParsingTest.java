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

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Parameterized unit tests for successfully parsing valid {@link TopicPath} strings.
 */
@RunWith(Parameterized.class)
public final class ImmutableTopicPathParameterizedParsingTest {

    private static final String NAMESPACE = "com.example";
    private static final String ENTITY_NAME = "myId";

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        final Object[][] data = {
                {"com.example/myId/things/twin/commands/modify", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .twin()
                        .commands()
                        .modify()
                        .build()},
                {"com.example/myId/things/twin/commands/create", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .twin()
                        .commands()
                        .create()
                        .build()},
                {"com.example/myId/things/twin/commands/delete", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .twin()
                        .commands()
                        .delete()
                        .build()},
                {"com.example/myId/things/twin/commands/retrieve", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .twin()
                        .commands()
                        .retrieve()
                        .build()},
                {"com.example/myId/things/twin/commands/merge", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .twin()
                        .commands()
                        .merge()
                        .build()},
                {"com.example/myId/things/twin/events/created", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .twin()
                        .events()
                        .created()
                        .build()},
                {"com.example/myId/things/twin/events/modified", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .twin()
                        .events()
                        .modified()
                        .build()},
                {"com.example/myId/things/twin/events/merged", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .twin()
                        .events()
                        .merged()
                        .build()},
                {"com.example/myId/things/twin/events/deleted", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .twin()
                        .events()
                        .deleted()
                        .build()},
                {"com.example/myId/things/live/events/created", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .live()
                        .events()
                        .created()
                        .build()},
                {"com.example/myId/things/live/events/modified", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .live()
                        .events()
                        .modified()
                        .build()},
                {"com.example/myId/things/live/events/merged", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .live()
                        .events()
                        .merged()
                        .build()},
                {"com.example/myId/things/live/events/deleted", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .live()
                        .events()
                        .deleted()
                        .build()},
                {"com.example/myId/things/twin/errors",
                        ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).things().twin().errors().build()},

                {"com.example/myId/things/live/messages/subject/with/multiple/slashes",
                        ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                                .things()
                                .live()
                                .messages()
                                .subject("subject/with/multiple/slashes")
                                .build()},

                {"com.example/myId/things/twin/acks/",
                        ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).things().twin().acks().build()},

                {"com.example/myId/things/twin/search/subscribe", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .twin()
                        .search()
                        .subscribe()
                        .build()},
                {"com.example/myId/things/twin/search/cancel", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .twin()
                        .search()
                        .cancel()
                        .build()},
                {"com.example/myId/things/twin/search/request", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .twin()
                        .search()
                        .request()
                        .build()},
                {"com.example/myId/things/twin/search/complete", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .twin()
                        .search()
                        .complete()
                        .build()},
                {"com.example/myId/things/twin/search/created", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .twin()
                        .search()
                        .generated()
                        .build()},
                {"com.example/myId/things/twin/search/failed", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .twin()
                        .search()
                        .failed()
                        .build()},
                {"com.example/myId/things/twin/search/next", ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME)
                        .things()
                        .twin()
                        .search()
                        .hasNext()
                        .build()},

                {"com.example/myId/policies/commands/modify",
                        ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).policies().commands().modify().build()},
                {"com.example/myId/policies/commands/retrieve",
                        ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).policies().commands().retrieve().build()},
                {"com.example/myId/policies/commands/create",
                        ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).policies().commands().create().build()},
                {"com.example/myId/policies/commands/delete",
                        ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).policies().commands().delete().build()},
                {"com.example/myId/policies/errors",
                        ImmutableTopicPath.newBuilder(NAMESPACE, ENTITY_NAME).policies().errors().build()}
        };
        return Arrays.asList(data);
    }

    @Parameterized.Parameter
    public String input;

    @Parameterized.Parameter(1)
    public TopicPath expected;

    @Test
    public void parseTopicPathString() {
        final ImmutableTopicPath actual = ImmutableTopicPath.parseTopicPath(input);

        assertThat(actual).isEqualTo(expected);
    }

}
