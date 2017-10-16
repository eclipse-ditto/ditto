# Ditto extensions for JSR-305

This module provides extensions for the annotations for Software Defect Detection defined by
[JSR-305](https://jcp.org/en/jsr/detail?id=305).
 
## Maven Coordinates

    <dependency>
        <groupId>org.eclipse.ditto</groupId>
        <artifactId>ditto-utils-jsr305</artifactId>
        <version>VERSION</version>
        <scope>provided</scope>
    </dependency>

## Prerequisites
An implementation of the JSR-305 annotations has to be on the class path in order to use the extensions.
By now we rely on the implementation of [FindBugs](https://github.com/findbugsproject/findbugs) with minimal
version 3.0.1:

    <dependency>
        <groupId>com.google.code.findbugs</groupId>
        <artifactId>jsr305</artifactId>
        <version>3.0.1</version>
        <scope>provided</scope>
    </dependency>
