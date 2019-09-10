# GSuite Sync

load("@rules_jvm_external//:defs.bzl", "artifact")

config_setting(
    name = "local",
    values = {"define": "profile=local"},
)

java_binary(
    name = "gsuite-sync",
    srcs = glob(["src/main/java/**/*.java"]),
    main_class = "com.kvaster.gsuite.App",
    resources = select({
        ":local": glob(["src/test/resources/**"]),
        "//conditions:default": glob(["src/main/resources/**"]),
    }),
    deps = [
        # logging
        artifact("org.slf4j:slf4j-api"),
        artifact("org.slf4j:log4j-over-slf4j"),
        artifact("org.slf4j:jcl-over-slf4j"),
        artifact("org.slf4j:jul-to-slf4j"),
        artifact("ch.qos.logback:logback-classic"),

        # app specific
        artifact("com.unboundid:unboundid-ldapsdk"),
        artifact("org.yaml:snakeyaml"),
        artifact("com.google.guava:guava"),
        artifact("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml"),
        artifact("com.fasterxml.jackson.core:jackson-databind"),
        artifact("com.fasterxml.jackson.core:jackson-core"),
        artifact("com.fasterxml.jackson.core:jackson-annotations"),
        artifact("com.sun.mail:mailapi"),
        artifact("com.sun.mail:smtp"),
        artifact("org.apache.commons:commons-email"),

        # google
        artifact("com.google.http-client:google-http-client-jackson2"),
        artifact("com.google.http-client:google-http-client"),
        artifact("com.google.api-client:google-api-client"),
        artifact("com.google.apis:google-api-services-admin-directory"),
        artifact("com.google.auth:google-auth-library-oauth2-http"),

        # test
        #artifact("org.junit.jupiter:junit-jupiter"),
    ],
)
