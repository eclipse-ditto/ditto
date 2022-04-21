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
package org.eclipse.ditto.things.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.junit.Test;

public final class ThingFieldSelectorTest {

    private static final Features FEATURES = Features.newBuilder()
            .set(Feature.newBuilder().withId("f1").build())
            .set(Feature.newBuilder().withId("f2").build())
            .build();

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
    public void test() {
        assertThatCode(() -> ThingFieldSelector.fromString(
                "/features/{{feature:id}}/definition,/features/{{fn:default('*')|fn:filter(topic:action,'eq','deleted')|fn:filter(resource.type,'eq','thing')|fn:filter(resource:path,'like','/|/features')}}/definition"))
                .doesNotThrowAnyException();
    }

    @Test
    public void fromJsonFieldSelectorWithThingFieldSelectorReturnsSameInstance() {
        final ThingFieldSelector initial = ThingFieldSelector.fromString("thingId");
        final ThingFieldSelector result = ThingFieldSelector.fromJsonFieldSelector(initial);
        assertThat(result).isSameAs(initial);
    }

    @Test
    public void testExpandFeatureIdWildcard() {
        final JsonFieldSelector fieldSelector = JsonFieldSelector.newInstance("thingId",
                "attributes", "features/*/properties/connected");
        final JsonFieldSelector expected = JsonFieldSelector.newInstance("thingId",
                "attributes", "features/f1/properties/connected", "features/f2/properties/connected");

        final JsonFieldSelector expanded =
                ThingsModelFactory.expandFeatureIdWildcards(FEATURES, fieldSelector);
        assertThat(expanded).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testExpandFeatureIdWithoutWildcard() {
        final JsonFieldSelector fieldSelector = JsonFieldSelector.newInstance("thingId",
                "attributes", "features/f1/properties/a", "features/f2/properties/b", "features/f3" +
                        "/properties/c");

        final JsonFieldSelector expanded =
                ThingsModelFactory.expandFeatureIdWildcards(FEATURES, fieldSelector);
        assertThat(expanded).containsExactlyInAnyOrderElementsOf(fieldSelector);
    }

}
