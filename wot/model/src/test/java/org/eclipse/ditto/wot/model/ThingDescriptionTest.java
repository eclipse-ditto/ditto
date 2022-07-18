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
package org.eclipse.ditto.wot.model;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for testing {@link ThingDescription}.
 */
public final class ThingDescriptionTest {

    private static final String SOME_EXAMPLE_TD = "/tds/some-example.td.json";

    private static String td;

    @BeforeClass
    public static void initTestFixture() {
        final URL tdUrl = ThingDescriptionTest.class.getResource(SOME_EXAMPLE_TD);
        final StringBuilder tdBuilder = new StringBuilder();
        try (final Stream<String> stream = Files.lines(Paths.get(tdUrl.toURI()), StandardCharsets.UTF_8)) {
            stream.forEach(line -> tdBuilder.append(line).append("\n"));
        } catch (final IOException | URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        td = tdBuilder.toString();
    }

    @Test
    public void testOverallParsingOfThingDescriptionFromJson() {
        final ThingDescription thingDescription = ThingDescription.fromJson(JsonFactory.newObject(td));
        assertThat(thingDescription.getTitle()).contains(Title.of("MyLampThing"));
        final Map<Locale, Title> expectedTitles = new HashMap<>();
        expectedTitles.put(Locale.GERMAN, Title.of("Mein Lampen Ding"));
        assertThat(thingDescription.getTitles()).contains(Titles.of(expectedTitles));
        assertThat(thingDescription.getId()).contains(IRI.of("urn:org.eclipse.ditto:333-WoTLamp-1234"));
        assertThat(thingDescription.getAtContext()).isEqualTo(MultipleAtContext.of(
                Arrays.asList(
                        SingleUriAtContext.W3ORG_2022_WOT_TD_V11,
                        SinglePrefixedAtContext.of("ditto", SingleUriAtContext.of("https://www.eclipse.org/ditto/ctx")),
                        SinglePrefixedAtContext.of("ace", SingleUriAtContext.of("http://www.example.org/ace-security#"))
                )
        ));
        assertThat(thingDescription.getBase())
                .contains(IRI.of("https://ditto.eclipseprojects.io/api/2/things/org.eclipse.ditto:333-WoTLamp-1234"));
        assertThat(thingDescription.getSecurity()).contains(SingleSecurity.of("basic_sc"));

        final Map<String, SecurityScheme> expectedSecurityDefinitions = new HashMap<>();
        expectedSecurityDefinitions.put("basic_sc", BasicSecurityScheme.newBuilder("basic_sc")
                .setIn("header")
                .build()
        );
        expectedSecurityDefinitions.put("ace_sc", AdditionalSecurityScheme.newBuilder("ace_sc", "ace:ACESecurityScheme")
                .setDescription(Description.of("Here be dragons"))
                .set("ace:custom", "foobar!")
                .build()
        );
        assertThat(thingDescription.getSecurityDefinitions())
                .contains(SecurityDefinitions.of(expectedSecurityDefinitions));

        final Map<String, Property> expectedProperties = new HashMap<>();
        expectedProperties.put("status", Property.newBuilder("status")
                .setType(DataSchemaType.STRING)
                .setForms(PropertyForms.of(singletonList(
                        PropertyFormElement.newBuilder()
                                .setHref(IRI.of("/attributes/status"))
                                .setOp(MultiplePropertyFormElementOp.of(Arrays.asList(
                                        SinglePropertyFormElementOp.READPROPERTY,
                                        SinglePropertyFormElementOp.WRITEPROPERTY,
                                        SinglePropertyFormElementOp.OBSERVEPROPERTY
                                )))
                                .build()
                )))
                .build()
        );
        assertThat(thingDescription.getProperties())
                .contains(Properties.of(expectedProperties));

        final Map<String, Action> expectedActions = new HashMap<>();
        expectedActions.put("toggle", Action.newBuilder("toggle")
                .setForms(ActionForms.of(singletonList(
                        ActionFormElement.newBuilder()
                                .setHref(IRI.of("/inbox/messages/toggle"))
                                .build()
                )))
                .build()
        );
        assertThat(thingDescription.getActions())
                .contains(Actions.of(expectedActions));

        final Map<String, Event> expectedEvents = new HashMap<>();
        expectedEvents.put("overheating", Event.newBuilder("overheating")
                .setData(StringSchema.newBuilder().build())
                .setForms(EventForms.of(singletonList(
                        EventFormElement.newBuilder()
                                .setHref(IRI.of("/outbox/messages/overheating"))
                                .setSubprotocol("sse")
                                .build()
                )))
                .build()
        );
        assertThat(thingDescription.getEvents())
                .contains(Events.of(expectedEvents));

        final List<BaseLink<?>> expectedLinks = new ArrayList<>();
        expectedLinks.add(Link.newBuilder()
                .setRel("service-doc")
                .setHref(IRI.of("https://eclipse.org/ditto/some-pdf.pdf"))
                .setType("application/pdf")
                .setHreflang(Hreflang.newSingleHreflang("de-CH-1996"))
                .build()
        );
        assertThat(thingDescription.getLinks())
                .contains(Links.of(expectedLinks));
    }

    @Test
    public void testBuildingThingDescriptionWithBuilder() {
        final Map<String, SecurityScheme> securityDefinitionsMap = new LinkedHashMap<>();
        securityDefinitionsMap.put("basic_sc", BasicSecurityScheme.newBuilder("basic_sc")
                .setIn("header")
                .build());
        securityDefinitionsMap.put("ace_sc", SecurityScheme.newAdditionalSecurityBuilder("ace_sc", "ace:ACESecurityScheme")
                .setDescription(Description.of("Here be dragons"))
                .set("ace:custom", "foobar!")
                .build());
        final ThingDescription thingDescription = ThingDescription.newBuilder()
                .setAtContext(MultipleAtContext.of(
                        Arrays.asList(
                                SingleUriAtContext.W3ORG_2022_WOT_TD_V11,
                                SinglePrefixedAtContext.of("ditto",
                                        SingleUriAtContext.of("https://www.eclipse.org/ditto/ctx")),
                                SinglePrefixedAtContext.of("ace",
                                        SingleUriAtContext.of("http://www.example.org/ace-security#"))
                        )
                ))
                .setId(IRI.of("urn:org.eclipse.ditto:333-WoTLamp-1234"))
                .setTitle(Title.of("MyLampThing"))
                .setTitles(Titles.of(singletonMap(Locale.GERMAN, Title.of("Mein Lampen Ding"))))
                .setSecurityDefinitions(SecurityDefinitions.of(securityDefinitionsMap))
                .setSecurity(SingleSecurity.of("basic_sc"))
                .setBase(IRI.of("https://ditto.eclipseprojects.io/api/2/things/org.eclipse.ditto:333-WoTLamp-1234"))
                .setProperties(Properties.of(singletonMap("status", Property.newBuilder("status")
                        .setType(DataSchemaType.STRING)
                        .setForms(PropertyForms.of(singletonList(PropertyFormElement.newBuilder()
                                .setHref(IRI.of("/attributes/status"))
                                .setOp(MultiplePropertyFormElementOp.of(Arrays.asList(
                                        SinglePropertyFormElementOp.READPROPERTY,
                                        SinglePropertyFormElementOp.WRITEPROPERTY,
                                        SinglePropertyFormElementOp.OBSERVEPROPERTY
                                )))
                                .build()
                        )))
                        .build()
                )))
                .setActions(Actions.of(singletonMap("toggle", Action.newBuilder("toggle")
                        .setForms(ActionForms.of(singletonList(ActionFormElement.newBuilder()
                                .setHref(IRI.of("/inbox/messages/toggle"))
                                .build()
                        )))
                        .build()
                )))
                .setEvents(Events.of(singletonMap("overheating", Event.newBuilder("overheating")
                        .setData(StringSchema.newBuilder().build())
                        .setForms(EventForms.of(singletonList(EventFormElement.newBuilder()
                                .setHref(IRI.of("/outbox/messages/overheating"))
                                .setSubprotocol("sse")
                                .build()
                        )))
                        .build()
                )))
                .setLinks(Links.of(Collections.singletonList(Link.newBuilder()
                        .setRel("service-doc")
                        .setHref(IRI.of("https://eclipse.org/ditto/some-pdf.pdf"))
                        .setType("application/pdf")
                        .setHreflang(Hreflang.newSingleHreflang("de-CH-1996"))
                        .build()
                )))
                .build();

        DittoJsonAssertions.assertThat(thingDescription.toJson()).isEqualTo(JsonFactory.readFrom(td));
    }
}
