/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.signals.commands.common.ShutdownReasonFactory}.
 */
public final class ShutdownReasonFactoryTest {

    private static final String NAMESPACE = "com.example.test";
    private static final ShutdownReasonType UNKNOWN_REASON_TYPE = ShutdownReasonType.Unknown.of("brace-yourselves");
    private static final String DETAILS = "Winter is coming!";

    private static JsonObject purgeNamespaceReasonJson;
    private static JsonObject genericReasonJson;

    @BeforeClass
    public static void initTestConstants() {
        purgeNamespaceReasonJson = JsonFactory.newObjectBuilder()
                .set(ShutdownReason.JsonFields.TYPE, ShutdownReasonType.Known.PURGE_NAMESPACE.toString())
                .set(ShutdownReason.JsonFields.DETAILS, NAMESPACE)
                .build();
        genericReasonJson = JsonFactory.newObjectBuilder()
                .set(ShutdownReason.JsonFields.TYPE, UNKNOWN_REASON_TYPE.toString())
                .set(ShutdownReason.JsonFields.DETAILS, DETAILS)
                .build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ShutdownReasonFactory.class, areImmutable());
    }

    @Test
    public void getPurgeNamespaceReasonFromJson() {
        assertThat(ShutdownReasonFactory.fromJson(purgeNamespaceReasonJson))
                .isEqualTo(PurgeNamespaceReason.of(NAMESPACE));
    }

    @Test
    public void getGenericReasonFromJson() {
        assertThat(ShutdownReasonFactory.fromJson(genericReasonJson))
                .isEqualTo(GenericReason.getInstance(UNKNOWN_REASON_TYPE, DETAILS));
    }

    @Test
    public void fromJsonWithoutTypeFails() {
        final JsonObject jsonObject = genericReasonJson.toBuilder()
                .remove(ShutdownReason.JsonFields.TYPE)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> ShutdownReasonFactory.fromJson(jsonObject))
                .withMessageContaining(ShutdownReason.JsonFields.TYPE.getPointer().toString())
                .withNoCause();
    }

    @Test
    public void getGenericReasonFromJsonWithoutDetailsWorks() {
        final JsonObject jsonObject = genericReasonJson.toBuilder()
                .remove(ShutdownReason.JsonFields.DETAILS)
                .build();

        assertThat(ShutdownReasonFactory.fromJson(jsonObject))
                .isEqualTo(GenericReason.getInstance(UNKNOWN_REASON_TYPE, null));
    }

    @Test
    public void getPurgeNamespaceReasonWithoutDetailsFails() {
        final JsonObject jsonObject = purgeNamespaceReasonJson.toBuilder()
                .remove(ShutdownReason.JsonFields.DETAILS)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> ShutdownReasonFactory.fromJson(jsonObject))
                .withMessageContaining(ShutdownReason.JsonFields.DETAILS.getPointer().toString())
                .withNoCause();
    }

}
