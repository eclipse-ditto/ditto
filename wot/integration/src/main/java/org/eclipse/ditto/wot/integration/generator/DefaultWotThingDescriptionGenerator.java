/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.integration.generator;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.wot.integration.config.ToThingDescriptionConfig;
import org.eclipse.ditto.wot.integration.config.WotConfig;
import org.eclipse.ditto.wot.integration.provider.WotThingModelFetcher;
import org.eclipse.ditto.wot.model.Action;
import org.eclipse.ditto.wot.model.ActionFormElement;
import org.eclipse.ditto.wot.model.ActionForms;
import org.eclipse.ditto.wot.model.Actions;
import org.eclipse.ditto.wot.model.AtType;
import org.eclipse.ditto.wot.model.BaseLink;
import org.eclipse.ditto.wot.model.BooleanSchema;
import org.eclipse.ditto.wot.model.Description;
import org.eclipse.ditto.wot.model.Event;
import org.eclipse.ditto.wot.model.EventFormElement;
import org.eclipse.ditto.wot.model.EventForms;
import org.eclipse.ditto.wot.model.Events;
import org.eclipse.ditto.wot.model.FormElementAdditionalResponse;
import org.eclipse.ditto.wot.model.FormElementAdditionalResponses;
import org.eclipse.ditto.wot.model.IRI;
import org.eclipse.ditto.wot.model.IntegerSchema;
import org.eclipse.ditto.wot.model.Link;
import org.eclipse.ditto.wot.model.Links;
import org.eclipse.ditto.wot.model.MultipleAtType;
import org.eclipse.ditto.wot.model.MultiplePropertyFormElementOp;
import org.eclipse.ditto.wot.model.MultipleRootFormElementOp;
import org.eclipse.ditto.wot.model.ObjectSchema;
import org.eclipse.ditto.wot.model.Properties;
import org.eclipse.ditto.wot.model.Property;
import org.eclipse.ditto.wot.model.PropertyFormElement;
import org.eclipse.ditto.wot.model.PropertyForms;
import org.eclipse.ditto.wot.model.RootFormElement;
import org.eclipse.ditto.wot.model.RootForms;
import org.eclipse.ditto.wot.model.SchemaDefinitions;
import org.eclipse.ditto.wot.model.SingleActionFormElementOp;
import org.eclipse.ditto.wot.model.SingleAtType;
import org.eclipse.ditto.wot.model.SingleDataSchema;
import org.eclipse.ditto.wot.model.SingleEventFormElementOp;
import org.eclipse.ditto.wot.model.SinglePropertyFormElementOp;
import org.eclipse.ditto.wot.model.SingleRootFormElementOp;
import org.eclipse.ditto.wot.model.StringSchema;
import org.eclipse.ditto.wot.model.ThingDescription;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.model.Title;
import org.eclipse.ditto.wot.model.UriVariables;
import org.eclipse.ditto.wot.model.Version;
import org.eclipse.ditto.wot.model.WotThingModelInvalidException;
import org.eclipse.ditto.wot.model.WotThingModelPlaceholderUnresolvedException;

import akka.actor.ActorSystem;

/**
 * Default Ditto specific implementation of {@link WotThingDescriptionGenerator}.
 */
@Immutable
final class DefaultWotThingDescriptionGenerator implements WotThingDescriptionGenerator {

    private static final DittoLogger LOGGER =
            DittoLoggerFactory.getLogger(DefaultWotThingDescriptionGenerator.class);

    private static final String TM_PLACEHOLDER_PL_GROUP = "pl";
    private static final Pattern TM_PLACEHOLDER_PATTERN =
            Pattern.compile("^.*[{]{2}(?<" + TM_PLACEHOLDER_PL_GROUP + ">[ -~]+)[}]{2}.*$");

    private static final String TM_EXTENDS = "tm:extends";
    private static final String TM_SUBMODEL = "tm:submodel";
    private static final String TM_SUBMODEL_INSTANCE_NAME = "instanceName";

    private static final String HTV_METHOD_NAME = "htv:methodName";
    private static final String SUBPROTOCOL_SSE = "sse";
    private static final String CONTENT_TYPE_TEXT_EVENT_STREAM = "text/event-stream";

    private static final String SCHEMA_DITTO_ERROR = "dittoError";
    private static final String DITTO_FIELDS_URI_VARIABLE = "fields";

    private final ToThingDescriptionConfig toThingDescriptionConfig;
    private final WotThingModelExtensionResolver thingModelExtensionResolver;

    DefaultWotThingDescriptionGenerator(final ActorSystem actorSystem,
            final WotConfig wotConfig,
            final WotThingModelFetcher thingModelFetcher) {
        this.toThingDescriptionConfig = checkNotNull(wotConfig, "wotConfig").getToThingDescriptionConfig();
        thingModelExtensionResolver = new DefaultWotThingModelExtensionResolver(thingModelFetcher,
                actorSystem.dispatchers().lookup("wot-dispatcher"));
    }

    @Override
    public ThingDescription generateThingDescription(final ThingId thingId,
            @Nullable final Thing thing,
            @Nullable final JsonObject placeholderLookupObject,
            @Nullable final String featureId,
            final ThingModel thingModel,
            final URL thingModelUrl,
            final DittoHeaders dittoHeaders) {

        // generation rules defined at: https://w3c.github.io/wot-thing-description/#thing-model-td-generation

        final ThingModel thingModelWithExtensions = thingModelExtensionResolver
                .resolveThingModelExtensions(thingModel, dittoHeaders);
        final ThingModel thingModelWithExtensionsAndImports = thingModelExtensionResolver
                .resolveThingModelRefs(thingModelWithExtensions, dittoHeaders);

        LOGGER.withCorrelationId(dittoHeaders)
                .debug("ThingModel after resolving extensions + refs: <{}>", thingModelWithExtensionsAndImports);

        final ThingModel cleanedTm = removeThingModelSpecificElements(thingModelWithExtensionsAndImports, dittoHeaders);

        final ThingDescription.Builder tdBuilder = ThingDescription.newBuilder(cleanedTm.toJson());
        addBase(tdBuilder, thingId, featureId);
        addInstanceVersion(tdBuilder, cleanedTm.getVersion().orElse(null));
        addThingDescriptionLinks(tdBuilder, thingModelUrl, null != featureId, thingId);
        convertThingDescriptionTmSubmodelLinksToItems(tdBuilder, dittoHeaders);
        addThingDescriptionTemplateFromConfig(tdBuilder);
        addThingDescriptionAdditionalMetadata(tdBuilder, thing);
        if (null == featureId) {
            addThingDescriptionForms(cleanedTm, tdBuilder, Thing.JsonFields.ATTRIBUTES.getPointer());
        } else {
            addThingDescriptionForms(cleanedTm, tdBuilder, Feature.JsonFields.PROPERTIES.getPointer());
        }

        tdBuilder.setUriVariables(provideUriVariables(
                DittoHeaderDefinition.CHANNEL.getKey(),
                DittoHeaderDefinition.TIMEOUT.getKey(),
                DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(),
                DITTO_FIELDS_URI_VARIABLE
        ));
        tdBuilder.setSchemaDefinitions(SchemaDefinitions.of(
                Map.of(SCHEMA_DITTO_ERROR, buildDittoErrorSchema())
        ));

        final ThingDescription thingDescription = resolvePlaceholders(tdBuilder.build(), placeholderLookupObject,
                dittoHeaders);
        LOGGER.withCorrelationId(dittoHeaders)
                .info("Created ThingDescription for thingId <{}> and featureId <{}>: <{}>", thingId, featureId,
                        thingDescription);
        return thingDescription;
    }

    private ObjectSchema buildDittoErrorSchema() {
        return ObjectSchema.newBuilder()
                .setTitle(Title.of("Ditto error."))
                .setDescription(Description.of(
                        "Provides additional information about an occurred error and how to resolve it."))
                .setProperties(Map.of(
                        DittoRuntimeException.JsonFields.STATUS.getPointer().getRoot().orElseThrow().toString(),
                        IntegerSchema.newBuilder()
                                .setTitle(Title.of("Status code."))
                                .setDescription(Description.of(
                                        "The status code of the error with HTTP status code semantics " +
                                                "(e.g.: 4xx for user errors, 5xx for server errors)."))
                                .setMinimum(400)
                                .setMaximum(599)
                                .build(),
                        DittoRuntimeException.JsonFields.ERROR_CODE.getPointer().getRoot().orElseThrow().toString(),
                        StringSchema.newBuilder()
                                .setTitle(Title.of("Error code identifier."))
                                .setDescription(Description.of(
                                        "The error code or identifier that uniquely identifies the error."))
                                .build(),
                        DittoRuntimeException.JsonFields.MESSAGE.getPointer().getRoot().orElseThrow().toString(),
                        StringSchema.newBuilder()
                                .setTitle(Title.of("Error message."))
                                .setDescription(Description.of(
                                        "The human readable message that explains what went wrong during the " +
                                                "execution of a command/message."))
                                .build(),
                        DittoRuntimeException.JsonFields.DESCRIPTION.getPointer().getRoot().orElseThrow().toString(),
                        StringSchema.newBuilder()
                                .setTitle(Title.of("Error description."))
                                .setDescription(Description.of(
                                        "Contains further information about the error e.g. a hint what caused the " +
                                                "problem and how to solve it."))
                                .build(),
                        DittoRuntimeException.JsonFields.HREF.getPointer().getRoot().orElseThrow().toString(),
                        StringSchema.newBuilder()
                                .setTitle(Title.of("Error link."))
                                .setDescription(Description.of(
                                        "A link to further information about the error and how to fix it."))
                                .setFormat("uri")
                                .build()
                ))
                .setRequired(List.of(
                        DittoRuntimeException.JsonFields.STATUS.getPointer().getRoot().orElseThrow().toString(),
                        DittoRuntimeException.JsonFields.ERROR_CODE.getPointer().getRoot().orElseThrow().toString(),
                        DittoRuntimeException.JsonFields.MESSAGE.getPointer().getRoot().orElseThrow().toString()
                ))
                .build();
    }

    private ThingModel removeThingModelSpecificElements(final ThingModel thingModel, final DittoHeaders dittoHeaders) {
        final ThingModel.Builder tmBuilder = thingModel.toBuilder();
        removeRelTmExtendsFromLinks(thingModel, tmBuilder);
        replaceAtType(thingModel, tmBuilder, dittoHeaders);
        removeTmRequired(tmBuilder);
        return tmBuilder.build();
    }

    /**
     * "If used, links element entry with {@code "rel":"tm:extends"} MUST be removed from the current Partial TD"
     */
    private void removeRelTmExtendsFromLinks(final ThingModel thingModel, final ThingModel.Builder tmBuilder) {
        thingModel.getLinks().ifPresent(links -> {
            final List<BaseLink<?>> keptLinks = new ArrayList<>();
            links.stream()
                    .filter(link -> link.getRel().filter(TM_EXTENDS::equals).isEmpty())
                    .forEach(keptLinks::add);
            tmBuilder.setLinks(Links.of(keptLinks));
        });
    }

    /**
     * "The tm:ThingModel value of the top-level @type MUST be replaced by the value Thing in the Partial TD instance."
     */
    private void replaceAtType(final ThingModel thingModel, final ThingModel.Builder tmBuilder,
            final DittoHeaders dittoHeaders) {

        final AtType atType = thingModel.getAtType().orElseThrow(() ->
                WotThingModelInvalidException.newBuilder("The WoT ThingModel did not contain the mandatory '@type'!")
                        .build());

        if (atType.equals(SingleAtType.of("tm:ThingModel"))) {
            tmBuilder.setAtType(AtType.newSingleAtType("Thing"));
        } else if (atType instanceof SingleAtType) {
            throw WotThingModelInvalidException.newBuilder(
                            "The WoT ThingModel must be of '@type' being 'tm:ThingModel'")
                    .dittoHeaders(dittoHeaders)
                    .build();
        } else if (atType instanceof MultipleAtType multipleAtType) {
            final List<SingleAtType> keptTypes = new ArrayList<>();
            final AtomicBoolean tmThingModelWasPresent = new AtomicBoolean(false);
            multipleAtType.stream()
                    .forEach(st -> {
                        if (st.equals(SingleAtType.of("tm:ThingModel"))) {
                            tmThingModelWasPresent.set(true);
                            // remove tm:ThingModel by not adding it again
                            keptTypes.add(AtType.newSingleAtType("Thing"));
                        } else {
                            keptTypes.add(st);
                        }
                    });
            if (!tmThingModelWasPresent.get()) {
                throw WotThingModelInvalidException.newBuilder(
                                "The WoT ThingModel must contain one '@type' being 'tm:ThingModel'")
                        .dittoHeaders(dittoHeaders)
                        .build();
            }
            tmBuilder.setAtType(MultipleAtType.of(keptTypes));
        }
    }

    private void removeTmRequired(final ThingModel.Builder builder) {
        builder.remove(ThingModel.JsonFields.TM_OPTIONAL);
    }

    private void addBase(final ThingDescription.Builder thingDescriptionBuilder,
            final ThingId thingId, @Nullable final String featureId) {
        if (null != featureId) {
            final String featurePath = "/features/" + featureId;
            thingDescriptionBuilder.setId(IRI.of("urn:" + thingId + featurePath))
                    .setBase(IRI.of(buildThingIdBasePath(thingId) + featurePath));
        } else {
            thingDescriptionBuilder.setId(IRI.of("urn:" + thingId))
                    .setBase(IRI.of(buildThingIdBasePath(thingId)));
        }
    }

    private void addInstanceVersion(final ThingDescription.Builder thingDescriptionBuilder,
            @Nullable final Version tmVersion) {
        final Version versionWithInstance = Optional.ofNullable(tmVersion)
                .map(Version::toBuilder)
                .map(vBuilder -> vBuilder.setInstance(tmVersion.getModel().orElseThrow()))
                .map(Version.Builder::build)
                .orElse(null);
        thingDescriptionBuilder.setVersion(versionWithInstance);
    }

    private String buildThingIdBasePath(final ThingId thingId) {
        return toThingDescriptionConfig.getBasePrefix() + "/api/2/things/" + thingId;
    }

    private void addThingDescriptionLinks(final ThingDescription.Builder tdBuilder, final URL tmUrl,
            boolean addCollectionLinkPointingToThingId, final ThingId thingId) {

        final List<BaseLink<?>> newLinks = new ArrayList<>(tdBuilder.build().getLinks()
                .map(links -> StreamSupport.stream(links.spliterator(), false))
                .orElseGet(Stream::empty)
                .toList());

        final Link typeLink = Link.newBuilder()
                .setRel("type")
                .setHref(IRI.of(tmUrl.toString()))
                .setType(ContentType.APPLICATION_TM_JSON.getValue())
                .build();
        newLinks.add(0, typeLink); // add at first position

        if (addCollectionLinkPointingToThingId) {
            final Link collectionLink = Link.newBuilder()
                    .setRel("collection")
                    .setHref(IRI.of(buildThingIdBasePath(thingId)))
                    .setType(ContentType.APPLICATION_TD_JSON.getValue())
                    .build();
            newLinks.add(0, collectionLink); // add at first position
        }

        tdBuilder.setLinks(newLinks);
    }

    private void convertThingDescriptionTmSubmodelLinksToItems(final ThingDescription.Builder tdBuilder,
            final DittoHeaders dittoHeaders) {

        tdBuilder.build().getLinks().ifPresent(links -> {
            final List<BaseLink<?>> newLinks = new ArrayList<>();
            links.stream()
                    .map(link -> {
                        if (link.getRel().filter(TM_SUBMODEL::equals).isPresent()) {
                            return Link.newBuilder()
                                    .setRel("item")
                                    .setType(ContentType.APPLICATION_TD_JSON.getValue())
                                    .setHref(IRI.of("/features/" + link.getValue(TM_SUBMODEL_INSTANCE_NAME)
                                            .filter(JsonValue::isString)
                                            .map(JsonValue::asString)
                                            .orElseThrow(() -> WotThingModelInvalidException
                                                    .newBuilder("The required 'instanceName' field of the " +
                                                            "'tm:submodel' link was not provided."
                                                    ).dittoHeaders(dittoHeaders)
                                                    .build())
                                    ))
                                    .build();
                        } else {
                            // preserve all non tm:submodel links:
                            return link;
                        }
                    })
                    .forEach(newLinks::add);
            tdBuilder.setLinks(Links.of(newLinks));
        });
    }

    private void addThingDescriptionTemplateFromConfig(final ThingDescription.Builder thingDescriptionBuilder) {
        thingDescriptionBuilder.setAll(toThingDescriptionConfig.getJsonTemplate());
    }

    private void addThingDescriptionForms(final ThingModel thingModel,
            final ThingDescription.Builder tdBuilder,
            final JsonPointer propertiesPath) {
        generateRootForms(thingModel, tdBuilder, propertiesPath);
        generatePropertiesForms(thingModel, tdBuilder, propertiesPath);
        generateActionsForms(thingModel, tdBuilder);
        generateEventsForms(thingModel, tdBuilder);
    }

    private void addThingDescriptionAdditionalMetadata(final ThingDescription.Builder tdBuilder,
            @Nullable final Thing thing) {
        if (null != thing) {
            if (toThingDescriptionConfig.addCreated()) {
                thing.getCreated().ifPresent(tdBuilder::setCreated);
            }
            if (toThingDescriptionConfig.addModified()) {
                thing.getModified().ifPresent(tdBuilder::setModified);
            }
        }
    }

    /**
     * https://w3c.github.io/wot-thing-description/#form-top-level
     */
    private void generateRootForms(final ThingModel thingModel,
            final ThingDescription.Builder tdBuilder,
            final JsonPointer propertiesPath) {

        final Optional<RootForms> thingModelForms = thingModel.getForms();
        final String writeUriVariablesParams = provideUriVariablesBag(
                DittoHeaderDefinition.CHANNEL.getKey(),
                DittoHeaderDefinition.TIMEOUT.getKey(),
                DittoHeaderDefinition.RESPONSE_REQUIRED.getKey());
        final String readUriVariablesParams = provideUriVariablesBag(
                DittoHeaderDefinition.CHANNEL.getKey(),
                DittoHeaderDefinition.TIMEOUT.getKey());
        final String readMultiplePropertiesUriVariablesParams = provideUriVariablesBag(
                DITTO_FIELDS_URI_VARIABLE,
                DittoHeaderDefinition.CHANNEL.getKey(),
                DittoHeaderDefinition.TIMEOUT.getKey());
        if (thingModelForms.isPresent()) {
            tdBuilder.setForms(thingModelForms.get()
                    .stream()
                    .map(rfe -> RootFormElement.fromJson(rfe.toBuilder()
                            .setHref(IRI.of(propertiesPath))
                            .setAdditionalResponses(provideAdditionalResponses())
                            .build()
                    ))
                    .toList()
            );
        } else {
            tdBuilder.setForms(List.of(
                    buildRootFormElement(SingleRootFormElementOp.READALLPROPERTIES,
                            propertiesPath + readUriVariablesParams,
                            "GET"
                    ),
                    buildRootFormElement(SingleRootFormElementOp.READMULTIPLEPROPERTIES,
                            propertiesPath + readMultiplePropertiesUriVariablesParams,
                            "GET"
                    ),
                    buildRootFormElement(SingleRootFormElementOp.WRITEALLPROPERTIES,
                            propertiesPath + writeUriVariablesParams,
                            "PUT"
                    ),
                    buildRootFormElement(SingleRootFormElementOp.WRITEMULTIPLEPROPERTIES,
                            propertiesPath + writeUriVariablesParams,
                            "PATCH",
                            builder -> builder.setContentType(ContentType.APPLICATION_MERGE_PATCH_JSON.getValue())
                    ),
                    buildRootFormElement(List.of(
                                    SingleRootFormElementOp.OBSERVEALLPROPERTIES,
                                    SingleRootFormElementOp.UNOBSERVEALLPROPERTIES
                            ),
                            propertiesPath,
                            "GET",
                            builder -> builder
                                    .setSubprotocol(SUBPROTOCOL_SSE)
                                    .setContentType(CONTENT_TYPE_TEXT_EVENT_STREAM)
                    ),
                    buildRootFormElement(List.of(
                                    SingleRootFormElementOp.SUBSCRIBEALLEVENTS,
                                    SingleRootFormElementOp.UNSUBSCRIBEALLEVENTS
                            ),
                            JsonPointer.of("/outbox/messages"),
                            "GET",
                            builder -> builder
                                    .setSubprotocol(SUBPROTOCOL_SSE)
                                    .setContentType(CONTENT_TYPE_TEXT_EVENT_STREAM)
                    )
            ));
        }
    }

    private RootFormElement buildRootFormElement(final SingleRootFormElementOp op,
            final CharSequence hrefPointer,
            final String htvMethodName
    ) {
        return buildRootFormElement(op, hrefPointer, htvMethodName, builder -> {});
    }

    private RootFormElement buildRootFormElement(final SingleRootFormElementOp op,
            final CharSequence hrefPointer,
            final String htvMethodName,
            final Consumer<RootFormElement.Builder> builderConsumer
    ) {
        final RootFormElement.Builder builder = RootFormElement.newBuilder()
                .setOp(op)
                .setHref(IRI.of(hrefPointer))
                .set(HTV_METHOD_NAME, htvMethodName)
                .setContentType(ContentType.APPLICATION_JSON.getValue());
        builderConsumer.accept(builder);
        builder.setAdditionalResponses(provideAdditionalResponses());
        return builder.build();
    }

    private RootFormElement buildRootFormElement(final Collection<SingleRootFormElementOp> ops,
            final JsonPointer hrefPointer,
            final String htvMethodName,
            final Consumer<RootFormElement.Builder> builderConsumer
    ) {
        final RootFormElement.Builder builder = RootFormElement.newBuilder()
                .setOp(MultipleRootFormElementOp.of(ops))
                .setHref(IRI.of(hrefPointer))
                .set(HTV_METHOD_NAME, htvMethodName)
                .setContentType(ContentType.APPLICATION_JSON.getValue());
        builderConsumer.accept(builder);
        builder.setAdditionalResponses(provideAdditionalResponses());
        return builder.build();
    }

    private void generatePropertiesForms(final ThingModel thingModel,
            final ThingDescription.Builder tdBuilder,
            final JsonPointer propertiesPointer) {

        final Optional<String> dittoExtensionPrefix = thingModel.getAtContext()
                .determinePrefixFor(DittoWotExtension.DITTO_WOT_EXTENSION);

        thingModel.getProperties()
                .map(Properties::entrySet)
                .map(Set::stream)
                .map(properties -> properties.map(propertyEntry -> {
                    final String propertyName = propertyEntry.getKey();
                    final Property property = propertyEntry.getValue();

                    final Optional<String> category = dittoExtensionPrefix.flatMap(prefix ->
                                    property.getValue(prefix + ":" + DittoWotExtension.DITTO_WOT_EXTENSION_CATEGORY)
                            )
                            .filter(JsonValue::isString)
                            .map(JsonValue::asString);
                    final JsonPointer pointer = category.map(JsonKey::of)
                            .map(propertiesPointer::addLeaf)
                            .orElse(propertiesPointer);

                    final JsonPointer propertyHref = pointer.addLeaf(JsonKey.of(propertyName));
                    final String writeUriVariablesParams = provideUriVariablesBag(
                            DittoHeaderDefinition.CHANNEL.getKey(),
                            DittoHeaderDefinition.TIMEOUT.getKey(),
                            DittoHeaderDefinition.RESPONSE_REQUIRED.getKey());
                    final String readUriVariablesParams = provideUriVariablesBag(
                            DittoHeaderDefinition.CHANNEL.getKey(),
                            DittoHeaderDefinition.TIMEOUT.getKey());
                    return property.getForms()
                            .map(propertyFormElements -> property.toBuilder()
                                    .setForms(PropertyForms.of(propertyFormElements.stream()
                                            .map(pfe -> pfe.toBuilder()
                                                    .setHref(IRI.of(propertyHref))
                                                    .setAdditionalResponses(provideAdditionalResponses())
                                                    .build()
                                            )
                                            .toList()
                                    ))
                                    .build()
                            )
                            .orElseGet(() -> {
                                        final List<PropertyFormElement> formElements = new ArrayList<>();
                                        if (!property.isWriteOnly()) {
                                            formElements.add(buildPropertyFormElement(SinglePropertyFormElementOp.READPROPERTY,
                                                    propertyHref + readUriVariablesParams,
                                                    "GET"
                                            ));
                                        }

                                        if (!property.isReadOnly()) {
                                            formElements.add(buildPropertyFormElement(SinglePropertyFormElementOp.WRITEPROPERTY,
                                                    propertyHref + writeUriVariablesParams,
                                                    "PUT"
                                            ));
                                            formElements.add(buildPropertyFormElement(SinglePropertyFormElementOp.WRITEPROPERTY,
                                                    propertyHref + writeUriVariablesParams,
                                                    "PATCH",
                                                    builder -> builder
                                                            .setContentType(
                                                                    ContentType.APPLICATION_MERGE_PATCH_JSON.getValue())
                                            ));
                                        }

                                        // only if explicitly set to "observable": false in the JSON, deactivate the
                                        // observe form elements:
                                        if (property.getValue(Property.JsonFields.OBSERVABLE).orElse(true)) {
                                            formElements.add(buildPropertyFormElement(List.of(
                                                            SinglePropertyFormElementOp.OBSERVEPROPERTY,
                                                            SinglePropertyFormElementOp.UNOBSERVEPROPERTY
                                                    ),
                                                    propertyHref,
                                                    "GET",
                                                    builder -> builder
                                                            .setSubprotocol(SUBPROTOCOL_SSE)
                                                            .setContentType(CONTENT_TYPE_TEXT_EVENT_STREAM)
                                            ));
                                        }

                                        return property.toBuilder()
                                                .setObservable(property.getValue(Property.JsonFields.OBSERVABLE)
                                                        .orElse(true))
                                                .setForms(PropertyForms.of(formElements))
                                                .build();
                                    }
                            );
                }))
                .map(Stream::toList)
                .map(Properties::from)
                .ifPresent(tdBuilder::setProperties);
    }

    private String provideUriVariablesBag(final String... includedVariables) {
        return Arrays.stream(includedVariables)
                .collect(Collectors.joining(",", "{?", "}"));
    }

    private UriVariables provideUriVariables(final String... includedDefinitions) {

        final Map<String, SingleDataSchema> uriVariables = new LinkedHashMap<>();
        final List<String> defs = Arrays.asList(includedDefinitions);
        if (defs.contains(DittoHeaderDefinition.CHANNEL.getKey())) {
            uriVariables.put(DittoHeaderDefinition.CHANNEL.getKey(), StringSchema.newBuilder()
                    .setTitle(Title.of("The Ditto channel to interact with."))
                    .setDescription(Description.of(
                            "Defines to which channel to route the command: 'twin' (digital twin) or " +
                                    "'live' (the device)."))
                    .setEnum(List.of(
                            JsonValue.of("twin"),
                            JsonValue.of("live")
                    ))
                    .setDefault(JsonValue.of("twin"))
                    .build()
            );
        }
        if (defs.contains(DittoHeaderDefinition.TIMEOUT.getKey())) {
            uriVariables.put(DittoHeaderDefinition.TIMEOUT.getKey(), IntegerSchema.newBuilder()
                    .setTitle(Title.of("The timeout to apply."))
                    .setDescription(Description.of(
                            "Defines how long the backend should wait (in seconds) for completion of the " +
                                    "request. " +
                                    "A value of '0' applies fire and forget semantics for the command."
                    ))
                    .setMinimum(0)
                    .setMaximum(60)
                    .setDefault(JsonValue.of(60))
                    .build()
            );
        }
        if (defs.contains(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey())) {
            uriVariables.put(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), BooleanSchema.newBuilder()
                    .setTitle(Title.of("If a response is required."))
                    .setDescription(Description.of(
                            "Defines whether a response is required to the API call or not."
                    ))
                    .setDefault(JsonValue.of(true))
                    .build()
            );
        }
        if (defs.contains(DITTO_FIELDS_URI_VARIABLE)) {
            uriVariables.put(DITTO_FIELDS_URI_VARIABLE, StringSchema.newBuilder()
                    .setTitle(Title.of("Fields to select."))
                    .setDescription(Description.of(
                            "Contains a comma-separated list of fields (e.g. property names) to be included in the returned JSON."
                    ))
                    .build()
            );
        }
        return UriVariables.of(uriVariables);
    }

    private PropertyFormElement buildPropertyFormElement(final SinglePropertyFormElementOp op,
            final CharSequence hrefPointer,
            final String htvMethodName
    ) {
        return buildPropertyFormElement(op, hrefPointer, htvMethodName, builder -> {});
    }

    private PropertyFormElement buildPropertyFormElement(final SinglePropertyFormElementOp op,
            final CharSequence hrefPointer,
            final String htvMethodName,
            final Consumer<PropertyFormElement.Builder> builderConsumer
    ) {
        final PropertyFormElement.Builder builder = PropertyFormElement.newBuilder()
                .setOp(op)
                .setHref(IRI.of(hrefPointer))
                .set(HTV_METHOD_NAME, htvMethodName)
                .setContentType(ContentType.APPLICATION_JSON.getValue());
        builderConsumer.accept(builder);
        builder.setAdditionalResponses(provideAdditionalResponses());
        return builder.build();
    }

    private PropertyFormElement buildPropertyFormElement(final Collection<SinglePropertyFormElementOp> ops,
            final CharSequence hrefPointer,
            final String htvMethodName,
            final Consumer<PropertyFormElement.Builder> builderConsumer
    ) {
        final PropertyFormElement.Builder builder = PropertyFormElement.newBuilder()
                .setOp(MultiplePropertyFormElementOp.of(ops))
                .setHref(IRI.of(hrefPointer))
                .set(HTV_METHOD_NAME, htvMethodName)
                .setContentType(ContentType.APPLICATION_JSON.getValue());
        builderConsumer.accept(builder);
        builder.setAdditionalResponses(provideAdditionalResponses());
        return builder.build();
    }

    private void generateActionsForms(final ThingModel thingModel,
            final ThingDescription.Builder tdBuilder) {

        thingModel.getActions()
                .map(Actions::entrySet)
                .map(Set::stream)
                .map(actions -> actions.map(actionEntry -> {
                    final String actionName = actionEntry.getKey();
                    final Action action = actionEntry.getValue();
                    final JsonPointer actionHref = JsonPointer.of("/inbox/messages/" + actionName);
                    final String uriVariablesParams = provideUriVariablesBag(
                            DittoHeaderDefinition.TIMEOUT.getKey(),
                            DittoHeaderDefinition.RESPONSE_REQUIRED.getKey());
                    return action.getForms()
                            .map(actionFormElements -> action.toBuilder()
                                    .setSynchronous(true)
                                    .setForms(ActionForms.of(actionFormElements.stream()
                                            .map(afe -> afe.toBuilder()
                                                    .setHref(IRI.of(actionHref))
                                                    .setAdditionalResponses(provideAdditionalResponses())
                                                    .build()
                                            )
                                            .toList()
                                    ))
                                    .build()
                            )
                            .orElseGet(() -> action.toBuilder()
                                    .setSynchronous(true)
                                    .setForms(ActionForms.of(List.of(
                                            buildActionFormElement(SingleActionFormElementOp.INVOKEACTION,
                                                    actionHref + uriVariablesParams)
                                    )))
                                    .build()
                            );
                }))
                .map(Stream::toList)
                .map(actionList -> Actions.fromJson(actionList.stream()
                        .map(a -> JsonField.newInstance(a.getActionName(), a.toJson()))
                        .collect(JsonCollectors.fieldsToObject()))
                )
                .ifPresent(tdBuilder::setActions);
    }

    private ActionFormElement buildActionFormElement(final SingleActionFormElementOp op,
            final CharSequence hrefPointer
    ) {
        return ActionFormElement.newBuilder()
                .setOp(op)
                .setHref(IRI.of(hrefPointer))
                .set(HTV_METHOD_NAME, "POST")
                .setContentType(ContentType.APPLICATION_JSON.getValue())
                .setAdditionalResponses(provideAdditionalResponses())
                .build();
    }

    private FormElementAdditionalResponses provideAdditionalResponses() {
        return FormElementAdditionalResponses.of(List.of(
                FormElementAdditionalResponse.newBuilder()
                        .setSuccess(false)
                        .setSchema(SCHEMA_DITTO_ERROR)
                        .build()
        ));
    }

    private void generateEventsForms(final ThingModel thingModel, final ThingDescription.Builder tdBuilder) {

        thingModel.getEvents()
                .map(Events::entrySet)
                .map(Set::stream)
                .map(events -> events.map(eventEntry -> {
                    final String eventName = eventEntry.getKey();
                    final Event event = eventEntry.getValue();
                    final JsonPointer eventHref = JsonPointer.of("/outbox/messages/" + eventName);
                    return event.getForms()
                            .map(eventFormElements -> event.toBuilder()
                                    .setForms(EventForms.of(eventFormElements.stream()
                                            .map(efe -> efe.toBuilder()
                                                    .setHref(IRI.of(eventHref))
                                                    .setAdditionalResponses(provideAdditionalResponses())
                                                    .build()
                                            )
                                            .toList()
                                    ))
                                    .build()
                            )
                            .orElseGet(() -> event.toBuilder()
                                    .setForms(EventForms.of(List.of(
                                            buildEventFormElement(SingleEventFormElementOp.SUBSCRIBEEVENT, eventHref)
                                    )))
                                    .build()
                            );
                }))
                .map(Stream::toList)
                .map(Events::from)
                .ifPresent(tdBuilder::setEvents);
    }

    private EventFormElement buildEventFormElement(final SingleEventFormElementOp op,
            final JsonPointer hrefPointer
    ) {
        return EventFormElement.newBuilder()
                .setOp(op)
                .setHref(IRI.of(hrefPointer))
                .set(HTV_METHOD_NAME, "GET")
                .setSubprotocol(SUBPROTOCOL_SSE)
                .setContentType(CONTENT_TYPE_TEXT_EVENT_STREAM)
                .setAdditionalResponses(provideAdditionalResponses())
                .build();
    }

    private ThingDescription resolvePlaceholders(final ThingDescription tdWithPotentialPlaceholders,
            @Nullable final JsonObject modelPlaceholders, final DittoHeaders dittoHeaders) {
        return ThingDescription.fromJson(
                resolvePlaceholders(tdWithPotentialPlaceholders.toJson(), modelPlaceholders, dittoHeaders)
        );
    }

    private JsonObject resolvePlaceholders(final JsonObject jsonObject, @Nullable final JsonObject modelPlaceholders,
            final DittoHeaders dittoHeaders) {
        return jsonObject.stream()
                .map(field -> {
                    final JsonKey key = field.getKey();
                    final JsonValue value = field.getValue();
                    if (value.isString()) {
                        return JsonField.newInstance(key, resolvePlaceholder(value.asString(), modelPlaceholders)
                                .orElseThrow(() -> WotThingModelPlaceholderUnresolvedException
                                        .newBuilder(value.asString())
                                        .dittoHeaders(dittoHeaders)
                                        .build()));
                    } else if (value.isObject()) {
                        return JsonField.newInstance(key,
                                resolvePlaceholders(value.asObject(), modelPlaceholders, dittoHeaders) // recurse!
                        );
                    } else if (value.isArray()) {
                        return JsonField.newInstance(key,
                                resolvePlaceholders(value.asArray(), modelPlaceholders, dittoHeaders)
                        );
                    } else {
                        return field;
                    }
                })
                .collect(JsonCollectors.fieldsToObject());
    }

    private JsonArray resolvePlaceholders(final JsonArray jsonArray, @Nullable final JsonObject modelPlaceholders,
            final DittoHeaders dittoHeaders) {
        return jsonArray.stream()
                .map(arrValue -> {
                    if (arrValue.isString()) {
                        return resolvePlaceholder(arrValue.asString(), modelPlaceholders)
                                .orElseThrow(() -> WotThingModelPlaceholderUnresolvedException
                                        .newBuilder(arrValue.asString())
                                        .dittoHeaders(dittoHeaders)
                                        .build());
                    } else if (arrValue.isObject()) {
                        return resolvePlaceholders(arrValue.asObject(), modelPlaceholders, dittoHeaders);
                    } else if (arrValue.isArray()) {
                        return resolvePlaceholders(arrValue.asArray(), modelPlaceholders, dittoHeaders); // recurse!
                    } else {
                        return arrValue;
                    }
                })
                .collect(JsonCollectors.valuesToArray());
    }

    private Optional<JsonValue> resolvePlaceholder(final String value, @Nullable final JsonObject modelPlaceholders) {
        final Matcher matcher = TM_PLACEHOLDER_PATTERN.matcher(value);
        if (matcher.matches()) {
            final String placeholderToResolve = matcher.group(TM_PLACEHOLDER_PL_GROUP).trim();
            if (null != modelPlaceholders) {
                return modelPlaceholders.getValue(placeholderToResolve)
                        .or(() -> Optional.ofNullable(toThingDescriptionConfig.getPlaceholders().get(placeholderToResolve)));
            } else {
                return Optional.ofNullable(toThingDescriptionConfig.getPlaceholders().get(placeholderToResolve));
            }
        } else {
            return Optional.of(JsonValue.of(value));
        }
    }

}
