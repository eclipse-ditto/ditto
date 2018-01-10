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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.bson.Document;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.mapping.ThingDocumentMapper;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ThingUpdateFactoryTest {

    @Mock
    private IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer;

    @Test
    public void createDeleteThingUpdate() {
        final LocalDateTime before = LocalDateTime.now().minus(1, ChronoUnit.SECONDS);

        final Document result = (Document) ThingUpdateFactory.createDeleteThingUpdate();
        final LocalDateTime deleteTime = LocalDateTime.ofInstant(
                result.get(PersistenceConstants.SET, Document.class)
                        .get(PersistenceConstants.FIELD_DELETED, Date.class)
                        .toInstant(),
                ZoneId.systemDefault());
        final LocalDateTime after = LocalDateTime.now().plus(1, ChronoUnit.SECONDS);
        assertThat(before.isBefore(deleteTime))
                .isTrue();
        assertThat(after.isAfter(deleteTime))
                .isTrue();
    }

    @Test
    public void createUpdateThingUpdate() {
        final Thing input = Mockito.mock(Thing.class);
        final Thing restricted = Thing.newBuilder()
                .setId(":thing")
                .build();

        final Document expected = new Document(PersistenceConstants.SET, ThingDocumentMapper.toDocument(restricted))
                .append(PersistenceConstants.UNSET, new Document(PersistenceConstants.FIELD_DELETED, 1));

        when(indexLengthRestrictionEnforcer.enforceRestrictions(any(Thing.class))).thenReturn(restricted);
        assertThat(ThingUpdateFactory.createUpdateThingUpdate(indexLengthRestrictionEnforcer, input))
                .isEqualTo(expected);
    }

}