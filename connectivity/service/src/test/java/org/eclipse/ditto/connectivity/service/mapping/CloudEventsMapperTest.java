package org.eclipse.ditto.connectivity.service.mapping;
/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
//import org.eclipse.ditto.base.model.common.DittoConstants;
//import org.eclipse.ditto.base.model.headers.DittoHeaders;
//import org.eclipse.ditto.connectivity.api.ExternalMessage;
//import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
//import org.eclipse.ditto.json.JsonFactory;
//import org.eclipse.ditto.json.JsonPointer;
//import org.eclipse.ditto.protocol.JsonifiableAdaptable;
//import org.eclipse.ditto.protocol.ProtocolFactory;
//import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
//import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
//import org.eclipse.ditto.things.model.ThingId;
//import org.junit.Before;
//import org.junit.Test;
//
//import java.util.Map;
//
//import static org.eclipse.ditto.connectivity.service.mapping.AbstractMessageMapper.extractPayloadAsString;
//import static org.junit.Assert.assertEquals;
//
//public class CloudEventsMapperTest {
//
//     private static final ThingId THING_ID = ThingId.of("thing:id");
//     private static final ProtocolAdapter ADAPTER = DittoProtocolAdapter.newInstance();
//
//  public static void main(String[] args) {
//    //
//      ThingId thingId = ThingId.of("org.eclipse.ditto:thingID");
//      final JsonifiableAdaptable adaptable =
//              ProtocolFactory.wrapAsJsonifiableAdaptable(ProtocolFactory.newAdaptableBuilder
//                              (ProtocolFactory.newTopicPathBuilder(thingId).things().twin().commands().modify().build())
//                      .withPayload(ProtocolFactory
//                              .newPayloadBuilder(JsonPointer.of("/features"))
//                              .withValue(JsonFactory.nullLiteral())
//                              .build())
//                      .build());
//      ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(
//                      Map.of(ExternalMessage.CONTENT_TYPE_HEADER, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE))
//              .withTopicPath(adaptable.getTopicPath())
//              .withText(adaptable.toJsonString())
//              .build();
//    System.out.println(extractPayloadAsString(message));
//  }
//
//     String payload =
//      "{\"specversion\": \"1.0\", \"id\":\"3212e\", \"source\":\"http:somesite.com\",\"type\":\"com.site.com\"}";
//    private CloudEventsMapper underTest;
//
//    @Before
//    public void setUp() {
//        underTest = new CloudEventsMapper();
//    }
//
//      @Test
//     public void validatePayload() {
//         Boolean expected = true;
//         Boolean actual = underTest.validatePayload(payload);
//         assertEquals(expected, actual);
//     }
//
//    @Test
//    private void CloudEventsStructuredMessage() {}
//}
