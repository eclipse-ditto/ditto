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
package org.eclipse.ditto.signals.commands.things;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributeResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributesResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyIdResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyIdResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;

/**
 * Registry which is capable of parsing {@link ThingCommandResponse}s from JSON.
 */
@Immutable
public final class ThingCommandResponseRegistry extends AbstractCommandResponseRegistry<ThingCommandResponse> {

    private ThingCommandResponseRegistry(final Map<String, JsonParsable<ThingCommandResponse>> parseStrategies) {
        super(parseStrategies);
    }

    public static ThingCommandResponseRegistry newInstance() {
        final Map<String, JsonParsable<ThingCommandResponse>> parseStrategies = new HashMap<>();

        parseStrategies.put(CreateThingResponse.TYPE, CreateThingResponse::fromJson);
        parseStrategies.put(ModifyThingResponse.TYPE, ModifyThingResponse::fromJson);
        parseStrategies.put(DeleteThingResponse.TYPE, DeleteThingResponse::fromJson);

        parseStrategies.put(ModifyAclResponse.TYPE, ModifyAclResponse::fromJson);
        parseStrategies.put(ModifyAclEntryResponse.TYPE, ModifyAclEntryResponse::fromJson);
        parseStrategies.put(DeleteAclEntryResponse.TYPE, DeleteAclEntryResponse::fromJson);

        parseStrategies.put(ModifyAttributesResponse.TYPE, ModifyAttributesResponse::fromJson);
        parseStrategies.put(DeleteAttributesResponse.TYPE, DeleteAttributesResponse::fromJson);
        parseStrategies.put(ModifyAttributeResponse.TYPE, ModifyAttributeResponse::fromJson);
        parseStrategies.put(DeleteAttributeResponse.TYPE, DeleteAttributeResponse::fromJson);

        parseStrategies.put(ModifyFeaturesResponse.TYPE, ModifyFeaturesResponse::fromJson);
        parseStrategies.put(DeleteFeaturesResponse.TYPE, DeleteFeaturesResponse::fromJson);
        parseStrategies.put(ModifyFeatureResponse.TYPE, ModifyFeatureResponse::fromJson);
        parseStrategies.put(DeleteFeatureResponse.TYPE, DeleteFeatureResponse::fromJson);

        parseStrategies.put(ModifyFeatureDefinitionResponse.TYPE, ModifyFeatureDefinitionResponse::fromJson);
        parseStrategies.put(DeleteFeatureDefinitionResponse.TYPE, DeleteFeatureDefinitionResponse::fromJson);

        parseStrategies.put(ModifyFeaturePropertiesResponse.TYPE, ModifyFeaturePropertiesResponse::fromJson);
        parseStrategies.put(DeleteFeaturePropertiesResponse.TYPE, DeleteFeaturePropertiesResponse::fromJson);
        parseStrategies.put(ModifyFeaturePropertyResponse.TYPE, ModifyFeaturePropertyResponse::fromJson);
        parseStrategies.put(DeleteFeaturePropertyResponse.TYPE, DeleteFeaturePropertyResponse::fromJson);

        parseStrategies.put(RetrieveThingsResponse.TYPE, RetrieveThingsResponse::fromJson);
        parseStrategies.put(RetrieveThingResponse.TYPE, RetrieveThingResponse::fromJson);

        parseStrategies.put(RetrieveAclResponse.TYPE, RetrieveAclResponse::fromJson);
        parseStrategies.put(RetrieveAclEntryResponse.TYPE, RetrieveAclEntryResponse::fromJson);

        parseStrategies.put(RetrievePolicyIdResponse.TYPE, RetrievePolicyIdResponse::fromJson);
        parseStrategies.put(ModifyPolicyIdResponse.TYPE, ModifyPolicyIdResponse::fromJson);

        parseStrategies.put(RetrieveAttributesResponse.TYPE, RetrieveAttributesResponse::fromJson);
        parseStrategies.put(RetrieveAttributeResponse.TYPE, RetrieveAttributeResponse::fromJson);

        parseStrategies.put(RetrieveFeaturesResponse.TYPE, RetrieveFeaturesResponse::fromJson);
        parseStrategies.put(RetrieveFeatureResponse.TYPE, RetrieveFeatureResponse::fromJson);

        parseStrategies.put(RetrieveFeatureDefinitionResponse.TYPE, RetrieveFeatureDefinitionResponse::fromJson);

        parseStrategies.put(RetrieveFeaturePropertiesResponse.TYPE, RetrieveFeaturePropertiesResponse::fromJson);
        parseStrategies.put(RetrieveFeaturePropertyResponse.TYPE, RetrieveFeaturePropertyResponse::fromJson);

        parseStrategies.put(ThingErrorResponse.TYPE, ThingErrorResponse::fromJson);

        return new ThingCommandResponseRegistry(parseStrategies);
    }

}
