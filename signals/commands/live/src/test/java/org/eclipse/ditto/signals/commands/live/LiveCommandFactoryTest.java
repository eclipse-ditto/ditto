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
package org.eclipse.ditto.signals.commands.live;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.signals.commands.base.assertions.CommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.live.base.LiveCommand;
import org.eclipse.ditto.signals.commands.live.modify.DeleteFeaturePropertiesLiveCommand;
import org.eclipse.ditto.signals.commands.live.modify.DeleteThingLiveCommand;
import org.eclipse.ditto.signals.commands.live.modify.ModifyAttributeLiveCommand;
import org.eclipse.ditto.signals.commands.live.modify.ModifyFeatureLiveCommand;
import org.eclipse.ditto.signals.commands.live.modify.ModifyFeaturesLiveCommand;
import org.eclipse.ditto.signals.commands.live.query.RetrieveAttributeLiveCommand;
import org.eclipse.ditto.signals.commands.live.query.RetrieveFeaturePropertyLiveCommand;
import org.eclipse.ditto.signals.commands.live.query.RetrieveFeaturesLiveCommand;
import org.eclipse.ditto.signals.commands.live.query.RetrieveThingLiveCommand;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttribute;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributes;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeature;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatures;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributes;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatures;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.eclipse.ditto.signals.commands.live.modify.CreateThingLiveCommand;
import org.eclipse.ditto.signals.commands.live.modify.DeleteAttributeLiveCommand;
import org.eclipse.ditto.signals.commands.live.modify.DeleteAttributesLiveCommand;
import org.eclipse.ditto.signals.commands.live.modify.DeleteFeatureLiveCommand;
import org.eclipse.ditto.signals.commands.live.modify.DeleteFeaturePropertyLiveCommand;
import org.eclipse.ditto.signals.commands.live.modify.DeleteFeaturesLiveCommand;
import org.eclipse.ditto.signals.commands.live.modify.ModifyAttributesLiveCommand;
import org.eclipse.ditto.signals.commands.live.modify.ModifyFeaturePropertiesLiveCommand;
import org.eclipse.ditto.signals.commands.live.modify.ModifyFeaturePropertyLiveCommand;
import org.eclipse.ditto.signals.commands.live.modify.ModifyThingLiveCommand;
import org.eclipse.ditto.signals.commands.live.query.RetrieveAttributesLiveCommand;
import org.eclipse.ditto.signals.commands.live.query.RetrieveFeatureLiveCommand;
import org.eclipse.ditto.signals.commands.live.query.RetrieveFeaturePropertiesLiveCommand;
import org.eclipse.ditto.signals.commands.live.query.RetrieveThingsLiveCommand;

/**
 * Unit test for {@link LiveCommandFactory}.
 */
public final class LiveCommandFactoryTest {

    private LiveCommandFactory underTest;

    /** */
    @Before
    public void setUp() {
        underTest = LiveCommandFactory.getInstance();
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(LiveCommandFactory.class,
                areImmutable(),
                assumingFields("mappingStrategies").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    /** */
    @Test
    public void liveCommandFactoryIsSingleton() {
        assertThat(underTest).isSameAs(LiveCommandFactory.getInstance());
    }

    /**
     * This test runs in Maven just fine. Under IntelliJ it fails because IntelliJ reacts to violations of {@code
     * @Nonnull} annotations by its own with an IllegalArgumentException.
     */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetLiveCommandForNullCommand() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.getLiveCommand(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void tryToGetLiveCommandForCommandWithUnknownType() {
        final Command<?> commandMock = Mockito.mock(Command.class);
        Mockito.when(commandMock.getType()).thenReturn("Harambe");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.getLiveCommand(commandMock))
                .withMessage("No mapping strategy for command <%s> available! The command type <%s> is unknown!",
                        commandMock, commandMock.getType())
                .withNoCause();
    }

    /** */
    @Test
    public void getCreateThingLiveCommandForCreateThing() {
        final CreateThing twinCommand = CreateThing.of(TestConstants.Thing.THING, null, DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, CreateThingLiveCommand.class);
    }

    /** */
    @Test
    public void getDeleteAttributeLiveCommandForDeleteAttribute() {
        final DeleteAttribute twinCommand = DeleteAttribute.of(TestConstants.Thing.THING_ID,
                TestConstants.Thing.LOCATION_ATTRIBUTE_POINTER, DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, DeleteAttributeLiveCommand.class);
    }

    /** */
    @Test
    public void getDeleteAttributesLiveCommandForDeleteAttributes() {
        final DeleteAttributes twinCommand = DeleteAttributes.of(TestConstants.Thing.THING_ID, DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, DeleteAttributesLiveCommand.class);
    }

    /** */
    @Test
    public void getDeleteFeatureLiveCommandForDeleteFeature() {
        final DeleteFeature twinCommand =
                DeleteFeature.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        DittoHeaders
                                .empty());
        createAndCheckLiveCommandFor(twinCommand, DeleteFeatureLiveCommand.class);
    }

    /** */
    @Test
    public void getDeleteFeaturePropertiesLiveCommandForDeleteFeatureProperties() {
        final DeleteFeatureProperties twinCommand =
                DeleteFeatureProperties.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, DeleteFeaturePropertiesLiveCommand.class);
    }

    /** */
    @Test
    public void getDeleteFeaturePropertyLiveCommandForDeleteFeatureProperty() {
        final DeleteFeatureProperty twinCommand =
                DeleteFeatureProperty.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_POINTER, DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, DeleteFeaturePropertyLiveCommand.class);
    }

    /** */
    @Test
    public void getDeleteFeaturesLiveCommandForDeleteFeatures() {
        final DeleteFeatures twinCommand = DeleteFeatures.of(TestConstants.Thing.THING_ID, DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, DeleteFeaturesLiveCommand.class);
    }

    /** */
    @Test
    public void getDeleteThingLiveCommandForDeleteThing() {
        final DeleteThing twinCommand = DeleteThing.of(TestConstants.Thing.THING_ID, DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, DeleteThingLiveCommand.class);
    }

    /** */
    @Test
    public void getModifyAttributeLiveCommandForModifyAttribute() {
        final ModifyAttribute twinCommand =
                ModifyAttribute.of(TestConstants.Thing.THING_ID, TestConstants.Thing.LOCATION_ATTRIBUTE_POINTER,
                        TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE, DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, ModifyAttributeLiveCommand.class);
    }

    /** */
    @Test
    public void getModifyAttributesLiveCommandForModifyAttributes() {
        final ModifyAttributes twinCommand =
                ModifyAttributes.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ATTRIBUTES,
                        DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, ModifyAttributesLiveCommand.class);
    }

    /** */
    @Test
    public void getModifyFeatureLiveCommandForModifyFeature() {
        final ModifyFeature twinCommand =
                ModifyFeature.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR,
                        DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, ModifyFeatureLiveCommand.class);
    }

    /** */
    @Test
    public void getModifyFeaturePropertiesLiveCommandForModifyFeatureProperties() {
        final ModifyFeatureProperties twinCommand =
                ModifyFeatureProperties.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES, DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, ModifyFeaturePropertiesLiveCommand.class);
    }

    /** */
    @Test
    public void getModifyFeaturePropertyLiveCommandForModifyFeatureProperty() {
        final ModifyFeatureProperty twinCommand =
                ModifyFeatureProperty.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_POINTER,
                        TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_VALUE, DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, ModifyFeaturePropertyLiveCommand.class);
    }

    /** */
    @Test
    public void getModifyFeaturesLiveCommandForModifyFeatures() {
        final ModifyFeatures twinCommand =
                ModifyFeatures.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FEATURES, DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, ModifyFeaturesLiveCommand.class);
    }

    /** */
    @Test
    public void getModifyThingLiveCommandForModifyThing() {
        final ModifyThing twinCommand = ModifyThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.THING, null,
                DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, ModifyThingLiveCommand.class);
    }

    /** */
    @Test
    public void getRetrieveAttributeLiveCommandForRetrieveAttribute() {
        final RetrieveAttribute twinCommand =
                RetrieveAttribute.of(TestConstants.Thing.THING_ID, TestConstants.Thing.LOCATION_ATTRIBUTE_POINTER,
                        DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, RetrieveAttributeLiveCommand.class);
    }

    /** */
    @Test
    public void getRetrieveAttributesLiveCommandForRetrieveAttributes() {
        final RetrieveAttributes twinCommand =
                RetrieveAttributes.of(TestConstants.Thing.THING_ID, TestConstants.JSON_FIELD_SELECTOR_ATTRIBUTES,
                        DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, RetrieveAttributesLiveCommand.class);
    }

    /** */
    @Test
    public void getRetrieveFeatureLiveCommandForRetrieveFeature() {
        final RetrieveFeature twinCommand =
                RetrieveFeature.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, RetrieveFeatureLiveCommand.class);
    }

    /** */
    @Test
    public void getRetrieveFeaturePropertiesLiveCommandForRetrieveFeatureProperties() {
        final RetrieveFeatureProperties twinCommand =
                RetrieveFeatureProperties.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.JSON_FIELD_SELECTOR_FEATURE_PROPERTIES, DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, RetrieveFeaturePropertiesLiveCommand.class);
    }

    /** */
    @Test
    public void getRetrieveFeaturePropertyLiveCommandForRetrieveFeatureProperty() {
        final RetrieveFeatureProperty twinCommand =
                RetrieveFeatureProperty.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_POINTER, DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, RetrieveFeaturePropertyLiveCommand.class);
    }

    /** */
    @Test
    public void getRetrieveFeaturesLiveCommandForRetrieveFeatures() {
        final RetrieveFeatures twinCommand = RetrieveFeatures.of(TestConstants.Thing.THING_ID, DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, RetrieveFeaturesLiveCommand.class);
    }

    /** */
    @Test
    public void getRetrieveThingLiveCommandForRetrieveThing() {
        final RetrieveThing twinCommand = RetrieveThing.of(TestConstants.Thing.THING_ID, DittoHeaders.empty());
        createAndCheckLiveCommandFor(twinCommand, RetrieveThingLiveCommand.class);
    }

    /** */
    @Test
    public void getRetrieveThingsLiveCommandForRetrieveThing() {
        final List<String> thingIds = Arrays.asList(":boatyMcBoatface", ":Harambe");
        final RetrieveThings twinCommand = RetrieveThings.getBuilder(thingIds)
                .dittoHeaders(DittoHeaders.empty())
                .build();
        createAndCheckLiveCommandFor(twinCommand, RetrieveThingsLiveCommand.class);
    }

    private void createAndCheckLiveCommandFor(final Command<?> twinCommand,
            final Class<? extends LiveCommand> expectedLiveCommandClass) {
        final LiveCommand liveCommand = underTest.getLiveCommand(twinCommand);

        assertThat(liveCommand)
                .withType(twinCommand.getType())
                .withResourcePath(twinCommand.getResourcePath())
                .withDittoHeaders(twinCommand.getDittoHeaders())
                .withManifest(twinCommand.getManifest())
                .hasJsonString(twinCommand.toJsonString())
                .isInstanceOf(expectedLiveCommandClass);
    }

}
