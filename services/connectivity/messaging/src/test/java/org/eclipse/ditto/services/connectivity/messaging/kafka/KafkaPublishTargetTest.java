/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.kafka.KafkaPublishTarget}.
 */
public class KafkaPublishTargetTest {

    @Test
    public void extractsSimpleTopicName() {
        final KafkaPublishTarget target = KafkaPublishTarget.fromTargetAddress("events");

        assertThat(target.getTopic()).isEqualTo("events");
        assertThat(target.getKey()).isEmpty();
        assertThat(target.getPartition()).isEmpty();
    }

    @Test
    public void extractsTopicAndKey() {
        final KafkaPublishTarget target = KafkaPublishTarget.fromTargetAddress("events/anyRandomKey");

        assertThat(target.getTopic()).isEqualTo("events");
        assertThat(target.getKey()).contains("anyRandomKey");
        assertThat(target.getPartition()).isEmpty();
    }

    @Test
    public void extractsTopicAndKeyWithSpecialChars() {
        final String keyWithSpecialChars = "anyRandom/key#withSpecial*chars";
        final KafkaPublishTarget target = KafkaPublishTarget.fromTargetAddress("events/" + keyWithSpecialChars);

        assertThat(target.getTopic()).isEqualTo("events");
        assertThat(target.getKey()).contains(keyWithSpecialChars);
        assertThat(target.getPartition()).isEmpty();
    }

    @Test
    public void extractsTopicAndPartition() {
        final KafkaPublishTarget target = KafkaPublishTarget.fromTargetAddress("events#3");
        assertThat(target.getTopic()).isEqualTo("events");
        assertThat(target.getKey()).isEmpty();
        assertThat(target.getPartition()).contains(3);
    }

    @Test
    public void ignoresMissingKeyAfterSeparatorThrowsError() {
        final KafkaPublishTarget target = KafkaPublishTarget.fromTargetAddress("events/");

        assertThat(target.getTopic()).isEqualTo("events");
        assertThat(target.getKey()).isEmpty();
        assertThat(target.getPartition()).isEmpty();
    }

    @Test
    public void ignoresMissingPartitionAfterSeparatorThrowsError() {
        final KafkaPublishTarget target = KafkaPublishTarget.fromTargetAddress("events#");

        assertThat(target.getTopic()).isEqualTo("events");
        assertThat(target.getKey()).isEmpty();
        assertThat(target.getPartition()).isEmpty();
    }

    @Test
    public void invalidCharsInTopicNameThrowError() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> KafkaPublishTarget.fromTargetAddress("events*andstuff"));
    }

    @Test
    public void invalidPartitionThrowsError() {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> KafkaPublishTarget.fromTargetAddress("events#notAnInteger"));
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(KafkaPublishTarget.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(KafkaPublishTarget.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void test() {
        assertThat(KafkaPublishTarget.fromTargetAddress("events").getTopic()).isEqualTo("events");
    }

    @Test(expected = NullPointerException.class)
    public void testNull() {
        KafkaPublishTarget.fromTargetAddress(null);
    }
}