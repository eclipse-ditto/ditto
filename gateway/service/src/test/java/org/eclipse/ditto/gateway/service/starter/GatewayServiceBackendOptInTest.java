/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.lang.reflect.Method;

import org.eclipse.ditto.base.service.DittoService;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Pins that {@link GatewayService} does NOT opt in to the persistence backend check of {@link DittoService}: it
 * declares no {@code requiresPersistenceBackend()} of its own and inherits {@code DittoService}'s, which returns
 * {@code false}.
 * <p>
 * The instance is a Mockito mock that calls the real methods: it skips the private constructor, so no config is loaded.
 */
public final class GatewayServiceBackendOptInTest {

    @Test
    public void doesNotRequirePersistenceBackend() throws Exception {
        final GatewayService service = Mockito.mock(GatewayService.class, Mockito.CALLS_REAL_METHODS);
        assertThatExceptionOfType(NoSuchMethodException.class)
                .isThrownBy(() -> GatewayService.class.getDeclaredMethod("requiresPersistenceBackend"));

        final Method method = DittoService.class.getDeclaredMethod("requiresPersistenceBackend");
        method.setAccessible(true);

        assertThat(method.getDeclaringClass()).isEqualTo(DittoService.class);
        assertThat(method.invoke(service)).isEqualTo(false);
    }

}
