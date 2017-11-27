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
package org.eclipse.ditto.services.thingsearch.updater.actors;


import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class SearchSynchronizationSuccessTest {

    @Test
    public void newInstance() throws Exception {
        final LocalDateTime ts = LocalDateTime.now().minus(2, ChronoUnit.SECONDS);
        final SearchSynchronizationSuccess searchSynchronizationSuccess = SearchSynchronizationSuccess.newInstance(ts);
        assertThat(searchSynchronizationSuccess).isNotNull();
        assertThat(searchSynchronizationSuccess.getUtcTimestamp()).isEqualTo(ts);
    }

    @Test
    public void equalsAndHashcode() {
        EqualsVerifier.forClass(SearchSynchronizationSuccess.class).verify();
    }

}