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
 package org.eclipse.ditto.protocol.mappingstrategies;

 import java.util.HashMap;
 import java.util.List;
 import java.util.Map;
 import java.util.stream.Collectors;

 import org.eclipse.ditto.json.JsonArray;
 import org.eclipse.ditto.json.JsonParseException;
 import org.eclipse.ditto.json.JsonValue;
 import org.eclipse.ditto.things.model.ThingId;
 import org.eclipse.ditto.protocol.Adaptable;
 import org.eclipse.ditto.protocol.JsonifiableMapper;
 import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;

 public final class RetrieveThingsCommandMappingStrategies extends AbstractThingMappingStrategies<RetrieveThings> {

     private static final RetrieveThingsCommandMappingStrategies INSTANCE =
             new RetrieveThingsCommandMappingStrategies();

     protected RetrieveThingsCommandMappingStrategies() {
         super(initMappingStrategies());
     }

     public static RetrieveThingsCommandMappingStrategies getInstance() {
         return INSTANCE;
     }

     private static Map<String, JsonifiableMapper<RetrieveThings>> initMappingStrategies() {
         final Map<String, JsonifiableMapper<RetrieveThings>> mappingStrategies = new HashMap<>();

         mappingStrategies.put(RetrieveThings.TYPE, adaptable -> RetrieveThings.getBuilder(thingsIdsFrom(adaptable))
                 .dittoHeaders(dittoHeadersFrom(adaptable))
                 .namespace(namespaceFrom(adaptable))
                 .selectedFields(selectedFieldsFrom(adaptable)).build());

         return mappingStrategies;
     }

     private static List<ThingId> thingsIdsFrom(final Adaptable adaptable) {
         final JsonArray array = adaptable.getPayload()
                 .getValue()
                 .filter(JsonValue::isObject)
                 .map(JsonValue::asObject)
                 .orElseThrow(() -> new JsonParseException("Adaptable payload was non existing or no JsonObject"))
                 .getValue(RetrieveThings.JSON_THING_IDS)
                 .filter(JsonValue::isArray)
                 .map(JsonValue::asArray)
                 .orElseThrow(() -> new JsonParseException("Could not map 'thingIds' value to expected JsonArray"));

         return array.stream()
                 .map(JsonValue::asString)
                 .map(ThingId::of)
                 .collect(Collectors.toList());
     }
 }
