## Eclipse Ditto :: WoT :: Model

This module contains a W3C WoT (Web of Things) Java model following the
[Web of Things (WoT) Thing Description 1.1](https://www.w3.org/TR/wot-thing-description11/).

### Example use

#### Adding Maven dependency

In order to use the model, add the dependency to your maven `pom.xml`:

```xml
<dependency>
    <groupId>org.eclipse.ditto</groupId>
    <artifactId>ditto-wot-model</artifactId>
    <version>${ditto.version}</version> <!-- the ditto-wot-model is available since "ditto.version" 2.4.0 -->
</dependency>
```

Please be aware that by adding this dependency, the following transitive dependencies will also be added 
(but nothing more):
```xml
<dependencies>
    <dependency>
        <groupId>org.eclipse.ditto</groupId>
        <artifactId>ditto-json</artifactId>
        <version>${ditto.version}</version>
    </dependency>
    <dependency>
        <groupId>org.eclipse.ditto</groupId>
        <artifactId>ditto-base-model</artifactId>
        <version>${ditto.version}</version>
    </dependency>

    <dependency>
        <groupId>com.eclipsesource.minimal-json</groupId>
        <artifactId>minimal-json</artifactId>
        <version>0.9.5</version>
    </dependency>
    <dependency>
        <groupId>org.atteo.classindex</groupId>
        <artifactId>classindex</artifactId>
        <version>3.11</version>
    </dependency>
</dependencies>
```

#### Reading a WoT Thing Model / Thing Description

In order to read a WoT TM/TD (either from file or via an HTTP endpoint), please follow e.g. the following example:

```java
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.wot.model.ThingDescription;

public final class Tester {

    public static void main(String... args) {

        URL tdUrl = Tester.class.getResource("some-example.td.json");
        StringBuilder tdBuilder = new StringBuilder();
        try (Stream<String> stream = Files.lines(Paths.get(tdUrl.toURI()), StandardCharsets.UTF_8)) {
            stream.forEach(line -> tdBuilder.append(line).append("\n"));
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        String tdString = tdBuilder.toString();
        ThingDescription thingDescription = ThingDescription.fromJson(JsonFactory.newObject(tdString));

        Optional<Title> title = thingDescription.getTitle();
        // use the object model as you like ...
    }
}
```


#### Building a WoT Thing Model / Thing Description via Builder API

For the other way around, to e.g. build a WoT TM/TD from Java code, please use the following example as first
inspiration:

```java
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import java.util.Arrays;
import java.util.Locale;

import org.eclipse.ditto.wot.model.*;

public final class TesterBuilder {

    public static void main(String... args) {

        ThingDescription thingDescription = ThingDescription.newBuilder()
                .setAtContext(AtContext.newMultipleAtContext(
                        Arrays.asList(
                                SingleUriAtContext.W3ORG_2022_WOT_TD_V11,
                                SinglePrefixedAtContext.of("ditto",
                                        SingleUriAtContext.of("https://www.eclipse.org/ditto/ctx"))
                        )
                ))
                .setId(IRI.of("urn:org.eclipse.ditto:333-WoTLamp-1234"))
                .setTitle(Title.of("MyLampThing"))
                .setTitles(Titles.of(singletonMap(Locale.GERMAN, Title.of("Mein Lampen Ding"))))
                .setSecurityDefinitions(SecurityDefinitions.of(singletonMap("basic_sc",
                        SecurityScheme.newBasicSecurityBuilder("basic_sc")
                                .setIn("header")
                                .build()))
                )
                .setSecurity(Security.newSingleSecurity("basic_sc"))
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
                .build();

        String tdJsonString = thingDescription.toJsonString();
    }
}
```
