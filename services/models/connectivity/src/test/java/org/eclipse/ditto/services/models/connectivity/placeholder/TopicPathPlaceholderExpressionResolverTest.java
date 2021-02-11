 /*
  * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
 package org.eclipse.ditto.services.models.connectivity.placeholder;

 import static org.assertj.core.api.Assertions.assertThat;

 import java.util.Arrays;

 import org.eclipse.ditto.model.placeholders.ExpressionResolver;
 import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
 import org.eclipse.ditto.model.placeholders.PlaceholderResolver;
 import org.eclipse.ditto.protocoladapter.ProtocolFactory;
 import org.eclipse.ditto.protocoladapter.TopicPath;
 import org.junit.BeforeClass;
 import org.junit.Test;

 public final class TopicPathPlaceholderExpressionResolverTest {
     private static final String THING_NAME = "foobar199";
     private static final String KNOWN_TOPIC = "org.eclipse.ditto/" + THING_NAME + "/things/twin/commands/modify";

     private static ExpressionResolver underTest;

     @BeforeClass
     public static void setupClass() {
         final TopicPath topic = ProtocolFactory.newTopicPath(KNOWN_TOPIC);

         final PlaceholderResolver topicPathResolver = PlaceholderFactory.newPlaceholderResolver(
                 TopicPathPlaceholder.newTopicPathPlaceholder(), topic);

         underTest = PlaceholderFactory.newExpressionResolver(Arrays.asList(topicPathResolver));
     }

     @Test
     public void testSuccessfulPlaceholderResolution() {

         assertThat(underTest.resolve("{{ topic:full }}"))
                 .contains(KNOWN_TOPIC);
         assertThat(underTest.resolve("{{ topic:entityId }}"))
                 .contains(THING_NAME);

         // verify different whitespace
         assertThat(underTest.resolve("{{topic:entityId }}"))
                 .contains(THING_NAME);
         assertThat(underTest.resolve("{{topic:entityName}}"))
                 .contains(THING_NAME);
         assertThat(underTest.resolve("{{        topic:entityId}}"))
                 .contains(THING_NAME);
     }

 }
