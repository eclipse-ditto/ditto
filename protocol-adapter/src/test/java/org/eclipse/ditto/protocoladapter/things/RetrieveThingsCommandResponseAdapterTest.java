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
 package org.eclipse.ditto.protocoladapter.things;

 import java.util.Optional;

 import org.eclipse.ditto.json.JsonFactory;
 import org.eclipse.ditto.json.JsonPointer;
 import org.eclipse.ditto.model.base.common.HttpStatus;
 import org.eclipse.ditto.protocoladapter.Adaptable;
 import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
 import org.eclipse.ditto.protocoladapter.LiveTwinTest;
 import org.eclipse.ditto.protocoladapter.Payload;
 import org.eclipse.ditto.protocoladapter.ProtocolAdapterTest;
 import org.eclipse.ditto.protocoladapter.TestConstants;
 import org.eclipse.ditto.protocoladapter.TopicPath;
 import org.eclipse.ditto.protocoladapter.TopicPathBuilder;
 import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
 import org.junit.Before;
 import org.junit.Test;

 public final class RetrieveThingsCommandResponseAdapterTest extends LiveTwinTest implements ProtocolAdapterTest {

     private RetrieveThingsCommandResponseAdapter underTest;

     @Before
     public void setUp() {
         underTest = RetrieveThingsCommandResponseAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
     }

     @Test
     public void retrieveThingsResponseToAdaptable() {
         retrieveThingsResponseToAdaptable("");
     }

     @Test
     public void retrieveThingsResponseToAdaptableWithWildcardNamespace() {
         retrieveThingsResponseToAdaptable(null);
     }

     private void retrieveThingsResponseToAdaptable(final String namespace) {
         final JsonPointer path = JsonPointer.empty();

         final TopicPathBuilder topicPathBuilder = TopicPath.fromNamespace(Optional.ofNullable(namespace).orElse("_"));
         final TopicPath topicPath =
                 (channel == TopicPath.Channel.LIVE ? topicPathBuilder.live() : topicPathBuilder.twin())
                         .things()
                         .commands()
                         .retrieve()
                         .build();

         final Adaptable expected = Adaptable.newBuilder(topicPath)
                 .withPayload(Payload.newBuilder(path).withValue(JsonFactory.newArray()
                         .add(TestConstants.THING.toJsonString())
                         .add(TestConstants.THING2.toJsonString()))
                         .withStatus(HttpStatus.OK).build())
                 .withHeaders(TestConstants.HEADERS_V_2).build();

         final RetrieveThingsResponse retrieveThingsResponse = RetrieveThingsResponse.of(JsonFactory.newArray()
                         .add(TestConstants.THING.toJsonString())
                         .add(TestConstants.THING2.toJsonString()),
                 namespace,
                 TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);

         final Adaptable actual = underTest.toAdaptable(retrieveThingsResponse, channel);

         assertWithExternalHeadersThat(actual).isEqualTo(expected);
     }


     @Test
     public void retrieveThingsResponseFromAdaptable() {
         retrieveThingsResponseFromAdaptable(TestConstants.NAMESPACE);
     }

     @Test
     public void retrieveThingsResponseWithWildcardNamespaceFromAdaptable() {
         retrieveThingsResponseFromAdaptable(null);
     }

     private void retrieveThingsResponseFromAdaptable(final String namespace) {
         final RetrieveThingsResponse expected = RetrieveThingsResponse.of(JsonFactory.newArray()
                         .add(TestConstants.THING.toJsonString())
                         .add(TestConstants.THING2.toJsonString()),
                 namespace,
                 TestConstants.DITTO_HEADERS_V_2);

         final TopicPath topicPath = TopicPath.fromNamespace(Optional.ofNullable(namespace).orElse("_"))
                 .things()
                 .twin()
                 .commands()
                 .retrieve()
                 .build();

         final JsonPointer path = JsonPointer.empty();
         final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                 .withPayload(Payload.newBuilder(path).withValue(JsonFactory.newArray()
                         .add(TestConstants.THING.toJsonString())
                         .add(TestConstants.THING2.toJsonString())).build())
                 .withHeaders(TestConstants.HEADERS_V_2).build();

         final RetrieveThingsResponse actual = underTest.fromAdaptable(adaptable);

         assertWithExternalHeadersThat(actual).isEqualTo(expected);
     }
 }
