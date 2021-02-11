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

 import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
 import org.eclipse.ditto.model.placeholders.PlaceholderResolver;
 import org.eclipse.ditto.protocoladapter.ProtocolFactory;
 import org.eclipse.ditto.protocoladapter.TopicPath;
 import org.junit.Test;

 public final class TopicPathPlaceholderResolverTest {

     @Test
     public void testPlaceholderResolvementBasedOnTopic() {
         final String fullPath = "org.eclipse.ditto/foo23/things/twin/commands/modify";
         final TopicPath topic = ProtocolFactory.newTopicPath(fullPath);

         final PlaceholderResolver<TopicPath> underTest = PlaceholderFactory.newPlaceholderResolver(
                 TopicPathPlaceholder.newTopicPathPlaceholder(), topic);

         assertThat(underTest.resolve("full"))
                 .contains(fullPath);
         assertThat(underTest.resolve("namespace"))
                 .contains("org.eclipse.ditto");
         assertThat(underTest.resolve("entityId"))
                 .contains("foo23");
         assertThat(underTest.resolve("group"))
                 .contains("things");
         assertThat(underTest.resolve("channel"))
                 .contains("twin");
         assertThat(underTest.resolve("criterion"))
                 .contains("commands");
         assertThat(underTest.resolve("action"))
                 .contains("modify");
     }

 }
