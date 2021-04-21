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
 package org.eclipse.ditto.protocoladapter.adaptables;

 import java.util.HashMap;
 import java.util.Map;

 import org.eclipse.ditto.json.JsonArray;
 import org.eclipse.ditto.json.JsonParseException;
 import org.eclipse.ditto.json.JsonValue;
 import org.eclipse.ditto.protocoladapter.Adaptable;
 import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
 import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;

 public final class RetrieveThingsCommandResponseMappingStrategies
         extends AbstractThingMappingStrategies<RetrieveThingsResponse> {

     private static final RetrieveThingsCommandResponseMappingStrategies INSTANCE =
             new RetrieveThingsCommandResponseMappingStrategies();

     protected RetrieveThingsCommandResponseMappingStrategies() {
         super(initMappingStrategies());
     }

     public static RetrieveThingsCommandResponseMappingStrategies getInstance() {
         return INSTANCE;
     }

     private static Map<String, JsonifiableMapper<RetrieveThingsResponse>> initMappingStrategies() {
         final Map<String, JsonifiableMapper<RetrieveThingsResponse>> mappingStrategies = new HashMap<>();

         mappingStrategies.put(RetrieveThingsResponse.TYPE,
                 adaptable -> RetrieveThingsResponse.of(thingsArrayFrom(adaptable),
                         namespaceFrom(adaptable), dittoHeadersFrom(adaptable)));

         return mappingStrategies;
     }

     protected static JsonArray thingsArrayFrom(final Adaptable adaptable) {
         return adaptable.getPayload()
                 .getValue()
                 .filter(JsonValue::isArray)
                 .map(JsonValue::asArray)
                 .orElseThrow(() -> JsonParseException.newBuilder().build());
     }
 }
