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
package org.eclipse.ditto.thingsearch.service.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.Test;
import org.mockito.Mockito;

/**
 * Pins that {@link SearchService} opts in to the persistence backend check of {@code DittoService}: it declares
 * {@code requiresPersistenceBackend()} itself, and the method returns {@code true}.
 * <p>
 * The instance is a Mockito mock that calls the real methods: it skips the private constructor, so no config is loaded.
 */
public final class SearchServiceBackendOptInTest {

    @Test
    public void requiresPersistenceBackend() throws Exception {
        final SearchService service = Mockito.mock(SearchService.class, Mockito.CALLS_REAL_METHODS);
        final Method method = SearchService.class.getDeclaredMethod("requiresPersistenceBackend");
        method.setAccessible(true);

        assertThat(method.getDeclaringClass()).isEqualTo(SearchService.class);
        assertThat(method.invoke(service)).isEqualTo(true);
    }

}
