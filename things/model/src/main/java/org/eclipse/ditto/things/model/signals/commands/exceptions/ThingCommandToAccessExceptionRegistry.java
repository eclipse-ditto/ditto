/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.signals.commands.exceptions;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.WithFeatureId;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandToExceptionRegistry;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttributes;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatures;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThing;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributes;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatures;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyPolicyId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttribute;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributes;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDefinition;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredProperty;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperties;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatures;
import org.eclipse.ditto.things.model.signals.commands.query.RetrievePolicyId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingDefinition;

/**
 * Registry to map thing commands to access exceptions.
 */
public final class ThingCommandToAccessExceptionRegistry extends AbstractCommandToExceptionRegistry<ThingCommand<?>,
        DittoRuntimeException> {

    private static final ThingCommandToAccessExceptionRegistry INSTANCE = createInstance();

    private ThingCommandToAccessExceptionRegistry(
            final Map<String, Function<ThingCommand<?>, DittoRuntimeException>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Returns an instance of {@code ThingCommandToAccessExceptionRegistry}.
     *
     * @return the instance.
     */
    public static ThingCommandToAccessExceptionRegistry getInstance() {
        return INSTANCE;
    }

    private static ThingCommandToAccessExceptionRegistry createInstance() {
        final Map<String, Function<ThingCommand<?>, DittoRuntimeException>> mappingStrategies = new HashMap<>();

        // modify
        mappingStrategies.put(CreateThing.TYPE, command -> ThingConflictException.newBuilder(command.getEntityId())
                .dittoHeaders(command.getDittoHeaders())
                .build());
        mappingStrategies.put(ModifyThing.TYPE, ThingCommandToAccessExceptionRegistry::commandToThingException);
        mappingStrategies.put(DeleteThing.TYPE, ThingCommandToAccessExceptionRegistry::commandToThingException);

        mappingStrategies.put(ModifyPolicyId.TYPE, ThingCommandToAccessExceptionRegistry::commandToThingException);

        mappingStrategies.put(ModifyThingDefinition.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToDefinitionException);
        mappingStrategies.put(DeleteThingDefinition.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToDefinitionException);

        mappingStrategies.put(ModifyAttributes.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToAttributesException);
        mappingStrategies.put(DeleteAttributes.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToAttributesException);
        mappingStrategies.put(ModifyAttribute.TYPE, ThingCommandToAccessExceptionRegistry::commandToAttributeException);
        mappingStrategies.put(DeleteAttribute.TYPE, ThingCommandToAccessExceptionRegistry::commandToAttributeException);

        mappingStrategies.put(ModifyFeatures.TYPE, ThingCommandToAccessExceptionRegistry::commandToFeaturesException);
        mappingStrategies.put(DeleteFeatures.TYPE, ThingCommandToAccessExceptionRegistry::commandToFeaturesException);
        mappingStrategies.put(ModifyFeature.TYPE, ThingCommandToAccessExceptionRegistry::commandToFeatureException);
        mappingStrategies.put(DeleteFeature.TYPE, ThingCommandToAccessExceptionRegistry::commandToFeatureException);

        mappingStrategies.put(ModifyFeatureProperties.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToFeaturePropertiesException);
        mappingStrategies.put(DeleteFeatureProperties.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToFeaturePropertiesException);
        mappingStrategies.put(ModifyFeatureProperty.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToFeaturePropertyException);
        mappingStrategies.put(DeleteFeatureProperty.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToFeaturePropertyException);
        mappingStrategies.put(ModifyFeatureDesiredProperties.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToFeatureDesiredPropertiesException);
        mappingStrategies.put(DeleteFeatureDesiredProperties.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToFeatureDesiredPropertiesException);
        mappingStrategies.put(ModifyFeatureDesiredProperty.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToFeatureDesiredPropertyException);
        mappingStrategies.put(DeleteFeatureDesiredProperty.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToFeatureDesiredPropertyException);

        // query
        mappingStrategies.put(RetrieveThing.TYPE, ThingCommandToAccessExceptionRegistry::commandToThingException);

        mappingStrategies.put(RetrievePolicyId.TYPE,
                command -> PolicyIdNotAccessibleException.newBuilder(command.getEntityId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());

        mappingStrategies.put(RetrieveThingDefinition.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToDefinitionException);
        mappingStrategies.put(RetrieveAttribute.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToAttributeException);
        mappingStrategies.put(RetrieveAttributes.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToAttributesException);
        mappingStrategies.put(RetrieveFeature.TYPE, ThingCommandToAccessExceptionRegistry::commandToFeatureException);
        mappingStrategies.put(RetrieveFeatures.TYPE, ThingCommandToAccessExceptionRegistry::commandToFeaturesException);
        mappingStrategies.put(RetrieveFeatureDefinition.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToFeatureDefinitionException);
        mappingStrategies.put(RetrieveFeatureProperties.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToFeaturePropertiesException);
        mappingStrategies.put(RetrieveFeatureProperty.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToFeaturePropertyException);
        mappingStrategies.put(RetrieveFeatureDesiredProperties.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToFeatureDesiredPropertiesException);
        mappingStrategies.put(RetrieveFeatureDesiredProperty.TYPE,
                ThingCommandToAccessExceptionRegistry::commandToFeatureDesiredPropertyException);

        return new ThingCommandToAccessExceptionRegistry(mappingStrategies);
    }

    private static ThingNotAccessibleException commandToThingException(final ThingCommand<?> command) {
        return ThingNotAccessibleException.newBuilder(command.getEntityId())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private static ThingDefinitionNotAccessibleException commandToDefinitionException(final ThingCommand<?> command) {
        return ThingDefinitionNotAccessibleException.newBuilder(command.getEntityId())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private static AttributesNotAccessibleException commandToAttributesException(final ThingCommand<?> command) {
        return AttributesNotAccessibleException.newBuilder(command.getEntityId())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private static AttributeNotAccessibleException commandToAttributeException(final ThingCommand<?> command) {
        return AttributeNotAccessibleException.newBuilder(command.getEntityId(), command.getResourcePath())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private static FeaturesNotAccessibleException commandToFeaturesException(final ThingCommand<?> command) {
        return FeaturesNotAccessibleException.newBuilder(command.getEntityId())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private static FeatureNotAccessibleException commandToFeatureException(final ThingCommand<?> command) {
        return FeatureNotAccessibleException.newBuilder(command.getEntityId(),
                ((WithFeatureId) command).getFeatureId())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private static FeatureDefinitionNotAccessibleException commandToFeatureDefinitionException(
            final ThingCommand<?> command) {
        return FeatureDefinitionNotAccessibleException.newBuilder(command.getEntityId(),
                ((WithFeatureId) command).getFeatureId())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private static FeaturePropertiesNotAccessibleException commandToFeaturePropertiesException(
            final ThingCommand<?> command) {
        return FeaturePropertiesNotAccessibleException.newBuilder(command.getEntityId(),
                ((WithFeatureId) command).getFeatureId())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private static FeaturePropertyNotAccessibleException commandToFeaturePropertyException(
            final ThingCommand<?> command) {
        return FeaturePropertyNotAccessibleException.newBuilder(command.getEntityId(),
                ((WithFeatureId) command).getFeatureId(), command.getResourcePath())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private static FeatureDesiredPropertiesNotAccessibleException commandToFeatureDesiredPropertiesException(
            final ThingCommand<?> command) {
        return FeatureDesiredPropertiesNotAccessibleException.newBuilder(command.getEntityId(),
                ((WithFeatureId) command).getFeatureId())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private static FeatureDesiredPropertyNotAccessibleException commandToFeatureDesiredPropertyException(
            final ThingCommand<?> command) {
        return FeatureDesiredPropertyNotAccessibleException.newBuilder(command.getEntityId(),
                ((WithFeatureId) command).getFeatureId(), command.getResourcePath())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

}
