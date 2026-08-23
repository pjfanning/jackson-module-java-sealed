package com.github.pjfanning.jackson.sealed;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import tools.jackson.core.Version;
import tools.jackson.core.util.VersionUtil;

/** The module's own version, read from a resource generated at build time. */
final class PackageVersion {

    static final Version VERSION = load();

    private PackageVersion() {
    }

    private static Version load() {
        Properties properties = new Properties();
        try (InputStream in = PackageVersion.class.getResourceAsStream("version.properties")) {
            if (in == null) {
                return Version.unknownVersion();
            }
            properties.load(in);
        } catch (IOException e) {
            return Version.unknownVersion();
        }
        return VersionUtil.parseVersion(properties.getProperty("version"), properties.getProperty("groupId"),
                properties.getProperty("artifactId"));
    }
}
