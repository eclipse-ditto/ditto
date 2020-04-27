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
 package org.eclipse.ditto.services.gateway.util.config.endpoints;

 import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
 import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
 import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

 import org.assertj.core.api.JUnitSoftAssertions;
 import org.junit.Rule;
 import org.junit.Test;

 import com.typesafe.config.Config;
 import com.typesafe.config.ConfigFactory;

 import akka.http.javadsl.model.MediaTypes;
 import nl.jqno.equalsverifier.EqualsVerifier;


 public final class GatewayHttpConfigTest {

     @Rule
     public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

     @Test
     public void assertImmutability() {
         assertInstancesOf(GatewayHttpConfig.class, areImmutable(),
                 assumingFields("schemaVersions", "queryParamsAsHeaders","additionalAcceptedMediaTypes")
                         .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements(),
                 assumingFields("redirectToHttpsBlacklistPattern").areNotModifiedAndDoNotEscape());
     }

     @Test
     public void testHashCodeAndEquals() {
         EqualsVerifier.forClass(GatewayHttpConfig.class)
                 .usingGetClass()
                 .verify();
     }

     @Test
     public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
         final GatewayHttpConfig underTest = GatewayHttpConfig.of(ConfigFactory.empty());

         softly.assertThat(underTest.getAdditionalAcceptedMediaTypes())
                 .as(HttpConfig.GatewayHttpConfigValue.ADDITIONAL_ACCEPTED_MEDIA_TYPES.getConfigPath())
                 .contains(MediaTypes.APPLICATION_OCTET_STREAM.toString());
     }

     @Test
     public void testMultipleCommaSeparatedMediaTypes() {
         final Config gatewayTestConfig = ConfigFactory.parseString("http {\n additional-accepted-media-types = " +
                 "\"application/json,application/x-www-form-urlencoded,text/plain\"\n}");

         final GatewayHttpConfig underTest = GatewayHttpConfig.of(gatewayTestConfig);

         softly.assertThat(underTest.getAdditionalAcceptedMediaTypes())
                 .as(HttpConfig.GatewayHttpConfigValue.ADDITIONAL_ACCEPTED_MEDIA_TYPES.getConfigPath())
                 .contains(MediaTypes.APPLICATION_JSON.toString(),
                         MediaTypes.APPLICATION_X_WWW_FORM_URLENCODED.toString(),
                         MediaTypes.TEXT_PLAIN.toString());
     }

     @Test
     public void testNonParsableMediaType() {
         final Config gatewayTestConfig = ConfigFactory.parseString("http {\n additional-accepted-media-types = \"application-json\"\n}");

         final GatewayHttpConfig underTest = GatewayHttpConfig.of(gatewayTestConfig);

         softly.assertThat(underTest.getAdditionalAcceptedMediaTypes())
                 .as(HttpConfig.GatewayHttpConfigValue.ADDITIONAL_ACCEPTED_MEDIA_TYPES.getConfigPath())
                 .contains("application-json");
     }
 }