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
 package org.eclipse.ditto.protocol.adapter.things;

 import java.util.Arrays;
 import java.util.Optional;

 import org.eclipse.ditto.json.JsonFactory;
 import org.eclipse.ditto.json.JsonPointer;
 import org.eclipse.ditto.things.model.ThingId;
 import org.eclipse.ditto.protocol.Adaptable;
 import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
 import org.eclipse.ditto.protocol.LiveTwinTest;
 import org.eclipse.ditto.protocol.Payload;
 import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
 import org.eclipse.ditto.protocol.TestConstants;
 import org.eclipse.ditto.protocol.TopicPath;
 import org.eclipse.ditto.protocol.TopicPathBuilder;
 import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
 import org.junit.Before;
 import org.junit.Test;

 public final class RetrieveThingsCommandAdapterTest extends LiveTwinTest implements ProtocolAdapterTest {

     private RetrieveThingsCommandAdapter underTest;

     @Before
     public void setUp() {
         underTest = RetrieveThingsCommandAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
     }

     @Test
     public void retrieveThingsToAdaptable() {
         retrieveThingsToAdaptableWith("org.eclipse.ditto.example");
     }

     @Test
     public void retrieveThingsToAdaptableWithWildcardNamespace() {
         retrieveThingsToAdaptableWith(null);
     }

     private void retrieveThingsToAdaptableWith(final String namespace) {
         final TopicPathBuilder topicPathBuilder = TopicPath.fromNamespace(Optional.ofNullable(namespace).orElse("_"));
         final TopicPath topicPath =
                 (channel == TopicPath.Channel.LIVE ? topicPathBuilder.live() : topicPathBuilder.twin())
                         .commands()
                         .retrieve()
                         .build();
         final JsonPointer path = JsonPointer.empty();

         final Adaptable expected = Adaptable.newBuilder(topicPath)
                 .withPayload(Payload.newBuilder(path)
                         .withValue(JsonFactory.newObject()
                                 .setValue("thingIds", JsonFactory.newArray()
                                         .add("org.eclipse.ditto.example:id1")
                                         .add("org.eclipse.ditto.example:id2"))).build())
                 .withHeaders(TestConstants.HEADERS_V_2)
                 .build();

         final String namespaceOfThings = "org.eclipse.ditto.example";
         final ThingId id1 = ThingId.of(namespaceOfThings, "id1");
         final ThingId id2 = ThingId.of(namespaceOfThings, "id2");

         final RetrieveThings retrieveThings = RetrieveThings.getBuilder(Arrays.asList(id1, id2))
                 .dittoHeaders(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE)
                 .namespace(namespace)
                 .build();

         final Adaptable actual = underTest.toAdaptable(retrieveThings, channel);

         assertWithExternalHeadersThat(actual).isEqualTo(expected);
     }

     @Test
     public void retrieveThingsFromAdaptableWithSpecificNamespace() {
         retrieveThingsFromAdaptable("org.eclipse.ditto.example");
     }

     @Test
     public void retrieveThingsFromAdaptableWithWildcardNamespace() {
         retrieveThingsFromAdaptable(null);
     }

     private void retrieveThingsFromAdaptable(final String namespace) {
         final String namespaceOfThings = "org.eclipse.ditto.example";
         final ThingId id1 = ThingId.of(namespaceOfThings, "id1");
         final ThingId id2 = ThingId.of(namespaceOfThings, "id2");
         final RetrieveThings expected =
                 RetrieveThings.getBuilder(
                         Arrays.asList(id1, id2))
                         .dittoHeaders(TestConstants.DITTO_HEADERS_V_2)
                         .namespace(namespace)
                         .build();
         final TopicPath topicPath = TopicPath.fromNamespace(Optional.ofNullable(namespace).orElse("_"))
                 .twin()
                 .commands()
                 .retrieve()
                 .build();

         final JsonPointer path = JsonPointer.empty();

         final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                 .withPayload(Payload.newBuilder(path)
                         .withValue(JsonFactory.newObject()
                                 .setValue("thingIds", JsonFactory.newArray()
                                         .add("org.eclipse.ditto.example:id1")
                                         .add("org.eclipse.ditto.example:id2"))).build())
                 .withHeaders(TestConstants.HEADERS_V_2)
                 .build();

         final RetrieveThings actual = underTest.fromAdaptable(adaptable);

         assertWithExternalHeadersThat(actual).isEqualTo(expected);
     }
 }
