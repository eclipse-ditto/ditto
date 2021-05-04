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
package org.eclipse.ditto.internal.utils.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Unit test for {@link CborFactoryLoader}.
 */
public final class CborFactoryLoaderTest {

    @Test
    public void getInstanceProducesSingleton() {
        final var firstInstance = CborFactoryLoader.getInstance();
        final var secondInstance = CborFactoryLoader.getInstance();

        assertThat(secondInstance).isSameAs(firstInstance);
    }

    @Test
    public void getCborFactoryOrThrowReturnsCachedCborFactory() {
        final var underTest = CborFactoryLoader.getInstance();
        final var firstCborFactory = underTest.getCborFactoryOrThrow();
        final var secondCborFactory = underTest.getCborFactoryOrThrow();

        assertThat(secondCborFactory).isSameAs(firstCborFactory);
    }

}
