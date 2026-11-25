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
package org.eclipse.ditto.things.service.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider;
import org.junit.Test;

/**
 * Tests which read journal {@link ThingsRootActor} hands to the WoT validation config supervisor.
 */
public final class ThingsRootActorWotReadJournalTest {

    @Test
    public void wotValidationConfigReadJournalIsTheOneOfTheWotEntityType() {
        final PersistenceBackendProvider provider = mock(PersistenceBackendProvider.class);
        final DittoReadJournal otherReadJournal = mock(DittoReadJournal.class);
        final DittoReadJournal wotReadJournal = mock(DittoReadJournal.class);
        when(provider.getReadJournal(anyString())).thenReturn(otherReadJournal);
        when(provider.getReadJournal("wot-validation-config")).thenReturn(wotReadJournal);

        final DittoReadJournal result = ThingsRootActor.wotValidationConfigReadJournal(provider);

        assertThat(result).isSameAs(wotReadJournal);
        verify(provider).getReadJournal("wot-validation-config");
        verify(provider, never()).getReadJournal();
    }

}
