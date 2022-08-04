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

package org.eclipse.ditto.protocol.adapter.connectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link DefaultConnectivityCommandAdapterProvider}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DefaultConnectivityCommandAdapterProviderTest {

    @Mock
    private HeaderTranslator headerTranslator;

    @Test
    public void containsAdapters() {
        final DefaultConnectivityCommandAdapterProvider underTest =
                new DefaultConnectivityCommandAdapterProvider(headerTranslator);

        assertThat(underTest.getAnnouncementAdapter()).isNotNull();
        assertThat(underTest.getAdapters()).containsExactlyInAnyOrder(underTest.getAnnouncementAdapter());
    }

    @Test
    public void testInvalidArguments() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new DefaultConnectivityCommandAdapterProvider(null));
    }

}
