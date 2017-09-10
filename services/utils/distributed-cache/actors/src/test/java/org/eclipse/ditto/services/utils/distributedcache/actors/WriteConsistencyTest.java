/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.distributedcache.actors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.Test;

import akka.cluster.ddata.Replicator;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit test for {@link WriteConsistencyTest}.
 */
public final class WriteConsistencyTest {

    /** */
    @Test
    public void toStringOfLocalReturnsExpected() {
        final WriteConsistency underTest = WriteConsistency.LOCAL;
        final String expected = underTest.name().toLowerCase(Locale.ENGLISH);

        final String actual = underTest.toString();

        assertThat(actual).isEqualTo(expected);
    }

    /** */
    @Test
    public void timeoutOfLocalIsZeroSeconds() {
        final Replicator.WriteConsistency replicatorConsistency = WriteConsistency.LOCAL.getReplicatorConsistency();
        final FiniteDuration timeout = replicatorConsistency.timeout();
        final long actualSeconds = timeout.toSeconds();

        assertThat(actualSeconds).isZero();
    }

    /** */
    @Test
    public void toStringOfMajorityReturnsExpected() {
        final WriteConsistency underTest = WriteConsistency.MAJORITY;
        final String expected = underTest.name().toLowerCase(Locale.ENGLISH);

        final String actual = underTest.toString();

        assertThat(actual).isEqualTo(expected);
    }

    /** */
    @Test
    public void timeoutOfMajorityIsExpected() {
        final byte expectedSeconds = 1;

        final Replicator.WriteConsistency replicatorConsistency = WriteConsistency.MAJORITY.getReplicatorConsistency();
        final FiniteDuration timeout = replicatorConsistency.timeout();
        final long actualSeconds = timeout.toSeconds();

        assertThat(actualSeconds).isEqualTo(expectedSeconds);
    }

}
