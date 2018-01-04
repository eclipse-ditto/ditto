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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class AttributesUpdateFactoryTest {

    @Mock
    private IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer;


    @Test
    public void createAttributesUpdates() {
        final JsonPointer pointer = JsonPointer.of("manufacturer");
        final JsonValue value = JsonValue.of("any-company");
        final JsonValue restrictedValue = JsonValue.of("restrictedManufacturer");

        when(indexLengthRestrictionEnforcer
                .enforceRestrictionsOnAttributeValue(any(JsonPointer.class), any(JsonValue.class)))
                .thenReturn(restrictedValue);

        final List<Bson> updates = AttributesUpdateFactory
                .createAttributesUpdates(indexLengthRestrictionEnforcer, pointer, value);

        assertThat(updates.size())
                .isEqualTo(3);
        assertThat(updates.get(0).toString().contains("restrictedManufacturer"))
                .isTrue();
        assertThat(updates.get(2).toString().contains("restrictedManufacturer"))
                .isTrue();

        verify(indexLengthRestrictionEnforcer)
                .enforceRestrictionsOnAttributeValue(pointer, value);
    }

    @Test
    public void createAttributesUpdate() {
        final Attributes attributes = Mockito.mock(Attributes.class);
        final Attributes restrictedAttributes = Attributes.newBuilder()
                .set("manufacturer", "restrictedManufacturer")
                .build();

        when(indexLengthRestrictionEnforcer.enforceRestrictions(any(Attributes.class)))
                .thenReturn(restrictedAttributes);

        final List<Bson> updates = AttributesUpdateFactory
                .createAttributesUpdate(indexLengthRestrictionEnforcer, attributes);

        assertThat(updates.size())
                .isEqualTo(3);
        assertThat(updates.get(0).toString().contains("restrictedManufacturer"))
                .isTrue();
        assertThat(updates.get(2).toString().contains("restrictedManufacturer"))
                .isTrue();

        verify(indexLengthRestrictionEnforcer)
                .enforceRestrictions(attributes);
    }

    @Test
    public void createAttributeDeletionUpdate() {
        assertThat(AttributesUpdateFactory.createAttributeDeletionUpdate(JsonPointer.of("manufacturer")))
                .isNotNull();
    }

    @Test
    public void deleteAttributes() {
        assertThat(AttributesUpdateFactory.deleteAttributes())
                .isNotNull();
    }

}