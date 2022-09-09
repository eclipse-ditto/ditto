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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link KafkaPublishTarget}.
 */
public class KafkaPublishTargetTest {

    private static final String DEFAULT_TOPIC = "events";

    @Test
    public void extractsSimpleTopicName() {
        final KafkaPublishTarget target = KafkaPublishTarget.fromTargetAddress(DEFAULT_TOPIC);

        assertThat(target.getTopic()).isEqualTo(DEFAULT_TOPIC);
        assertThat(target.getKey()).isEmpty();
        assertThat(target.getPartition()).isEmpty();
    }

    @Test
    public void extractsTopicAndKey() {
        final KafkaPublishTarget target = KafkaPublishTarget.fromTargetAddress("events/anyRandomKey");

        assertThat(target.getTopic()).isEqualTo(DEFAULT_TOPIC);
        assertThat(target.getKey()).contains("anyRandomKey");
        assertThat(target.getPartition()).isEmpty();
    }

    @Test
    public void extractsTopicAndKeyWithSpecialChars() {
        final String keyWithSpecialChars = "anyRandom/key#withSpecial*chars";
        final KafkaPublishTarget target = KafkaPublishTarget.fromTargetAddress("events/" + keyWithSpecialChars);

        assertThat(target.getTopic()).isEqualTo(DEFAULT_TOPIC);
        assertThat(target.getKey()).contains(keyWithSpecialChars);
        assertThat(target.getPartition()).isEmpty();
    }

    @Test
    public void extractsTopicAndPartition() {
        final KafkaPublishTarget target = KafkaPublishTarget.fromTargetAddress("events#3");
        assertThat(target.getTopic()).isEqualTo(DEFAULT_TOPIC);
        assertThat(target.getKey()).isEmpty();
        assertThat(target.getPartition()).contains(3);
    }

    @Test
    public void ignoresMissingKeyAfterSeparatorThrowsError() {
        final KafkaPublishTarget target = KafkaPublishTarget.fromTargetAddress("events/");

        assertThat(target.getTopic()).isEqualTo(DEFAULT_TOPIC);
        assertThat(target.getKey()).isEmpty();
        assertThat(target.getPartition()).isEmpty();
    }

    @Test
    public void ignoresMissingPartitionAfterSeparatorThrowsError() {
        final KafkaPublishTarget target = KafkaPublishTarget.fromTargetAddress("events#");

        assertThat(target.getTopic()).isEqualTo(DEFAULT_TOPIC);
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

    @Test(expected = NullPointerException.class)
    public void testNull() {
        KafkaPublishTarget.fromTargetAddress(null);
    }

}
