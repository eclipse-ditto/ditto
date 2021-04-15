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
package org.eclipse.ditto.model.things;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.junit.Test;

public final class ThingFieldSelectorTest {

    @Test
    public void fromNullStringThrows() {
        assertThatExceptionOfType(InvalidThingFieldSelectionException.class)
                .isThrownBy(() -> ThingFieldSelector.fromString(null))
                .withMessage("Thing field selection <null> was not valid.");
    }

    @Test
    public void fromNullSelectorThrows() {
        assertThatExceptionOfType(InvalidThingFieldSelectionException.class)
                .isThrownBy(() -> ThingFieldSelector.fromJsonFieldSelector(null))
                .withMessage("Thing field selection <null> was not valid.");
    }

    @Test
    public void fromStringWithInvalidFieldThrows() {
        assertThatExceptionOfType(InvalidThingFieldSelectionException.class)
                .isThrownBy(() -> ThingFieldSelector.fromString("faetures"))
                .withMessage("Thing field selection <faetures> was not valid.");
    }

    @Test
    public void fromJsonFieldSelectorWithInvalidFieldThrows() {
        final JsonFieldSelector invalidFieldSelector = JsonFieldSelector.newInstance("faetures");
        assertThatExceptionOfType(InvalidThingFieldSelectionException.class)
                .isThrownBy(() -> ThingFieldSelector.fromJsonFieldSelector(invalidFieldSelector))
                .withMessage("Thing field selection </faetures> was not valid.");
    }

    @Test
    public void leadingSlashIsAllowed() {
        assertThatCode(() -> ThingFieldSelector.fromString("/thingId"))
                .doesNotThrowAnyException();
    }

    @Test
    public void leadingSlashIsOptional() {
        assertThatCode(() -> ThingFieldSelector.fromString("thingId"))
                .doesNotThrowAnyException();
    }

    @Test
    public void fromValidFieldSelector() {
        assertThatCode(() -> ThingFieldSelector.fromJsonFieldSelector(JsonFieldSelector.newInstance("thingId")))
                .doesNotThrowAnyException();
    }

    @Test
    public void trailingSlashForFeaturesIsNotAllowed() {
        assertThatExceptionOfType(InvalidThingFieldSelectionException.class)
                .isThrownBy(() -> ThingFieldSelector.fromString("features/"));
    }

    @Test
    public void multipleValidFieldsAreAllowed() {
        assertThatCode(() -> ThingFieldSelector.fromString("thingId,features"))
                .doesNotThrowAnyException();
    }

    @Test
    public void secondFieldInvalidThrows() {
        assertThatExceptionOfType(InvalidThingFieldSelectionException.class)
                .isThrownBy(() -> ThingFieldSelector.fromString("features,thingd"));
    }

    @Test
    public void commaInFeatureNameIsNotSupported() {
        assertThatExceptionOfType(InvalidThingFieldSelectionException.class)
                .isThrownBy(() -> ThingFieldSelector.fromString("features/name,withcomma,thingd"));
    }

    @Test
    public void fromJsonFieldSelectorWithThingFieldSelectorReturnsSameInstance() {
        final ThingFieldSelector initial = ThingFieldSelector.fromString("thingId");
        final ThingFieldSelector result = ThingFieldSelector.fromJsonFieldSelector(initial);
        assertThat(result).isSameAs(initial);
    }

}
