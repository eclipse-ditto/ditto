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
 import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

 import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
 import org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException;
 import org.eclipse.ditto.model.things.ThingId;
 import org.eclipse.ditto.protocoladapter.TopicPath;
 import org.junit.Test;

 public final class TopicPathPlaceholderFilterTest {

     private static final String KNOWN_NAMESPACE = "org.eclipse.ditto.test";
     private static final String KNOWN_ID = "myThing";

     private static final String KNOWN_SUBJECT = "mySubject";
     private static final String KNOWN_SUBJECT2 = "$set.configuration/steps";

     private static final TopicPath KNOWN_TOPIC_PATH = TopicPath.newBuilder(ThingId.of(KNOWN_NAMESPACE, KNOWN_ID))
             .twin().things().commands().modify().build();
     private static final TopicPath KNOWN_TOPIC_PATH_SUBJECT1 =
             TopicPath.newBuilder(ThingId.of(KNOWN_NAMESPACE, KNOWN_ID))
                     .live().things().messages().subject(KNOWN_SUBJECT).build();
     private static final TopicPath KNOWN_TOPIC_PATH_SUBJECT2 =
             TopicPath.newBuilder(ThingId.of(KNOWN_NAMESPACE, KNOWN_ID))
                     .live().things().messages().subject(KNOWN_SUBJECT2).build();

      private static final TopicPathPlaceholder topicPlaceholder = TopicPathPlaceholder.newTopicPathPlaceholder();


     @Test
     public void testTopicPlaceholder() {
         assertThatExceptionOfType(NullPointerException.class).isThrownBy(
                 () -> topicPlaceholder.resolve(KNOWN_TOPIC_PATH, null));
         assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                 () -> topicPlaceholder.resolve(KNOWN_TOPIC_PATH, ""));
         assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                 () -> PlaceholderFilter.apply("{{ topic:unknown }}", KNOWN_TOPIC_PATH, topicPlaceholder));
         assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                 () -> PlaceholderFilter.apply("{{ {{  topic:name  }} }}", KNOWN_TOPIC_PATH, topicPlaceholder));
         assertThat(PlaceholderFilter.apply("eclipse:ditto", KNOWN_TOPIC_PATH, topicPlaceholder)).isEqualTo(
                 "eclipse:ditto");
         assertThat(PlaceholderFilter.apply("prefix:{{ topic:channel }}:{{ topic:group }}:suffix", KNOWN_TOPIC_PATH,
                 topicPlaceholder)).isEqualTo("prefix:twin:things:suffix");

         assertThat(PlaceholderFilter.apply("{{topic:subject}}", KNOWN_TOPIC_PATH_SUBJECT1,
                 topicPlaceholder)).isEqualTo(KNOWN_SUBJECT);
         assertThat(PlaceholderFilter.apply("{{  topic:action-subject}}", KNOWN_TOPIC_PATH_SUBJECT2,
                 topicPlaceholder)).isEqualTo(KNOWN_SUBJECT2);
     }
 }
