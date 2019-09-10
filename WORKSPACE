load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

RULES_JVM_EXTERNAL_TAG = "2.7"

RULES_JVM_EXTERNAL_SHA = "f04b1466a00a2845106801e0c5cec96841f49ea4e7d1df88dc8e4bf31523df74"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

# google apis
GOOGLE_API_CLIENT_VERSION = "1.30.2"

GOOGLE_DIRECTORY_SERVICE_VERSION = "directory_v1-rev110-1.25.0"

GOOGLE_OAUTH_HTTP_VERSION = "0.17.0"

# common libs
COMMONS_EMAIL_VERSION = "1.5"

JUNIT_VERSION = "5.5.1"

JACKSON_VERSION = "2.9.9"

JACKSON_DATABIND_VERSION = "2.9.9.3"

JAVA_MAIL_VERSION = "1.6.4"

LOGBACK_VERSION = "1.2.3"

SLF4J_VERSION = "1.7.26"

UNBOUNDID_LDAP_SDK_VERSION = "4.0.11"

SNAKE_YAML_VERSION = "1.25"

maven_install(
    artifacts = [
        "org.slf4j:slf4j-api:%s" % SLF4J_VERSION,
        "org.slf4j:log4j-over-slf4j:%s" % SLF4J_VERSION,
        "org.slf4j:jcl-over-slf4j:%s" % SLF4J_VERSION,
        "org.slf4j:jul-to-slf4j:%s" % SLF4J_VERSION,
        "ch.qos.logback:logback-classic:%s" % LOGBACK_VERSION,

        # app specific
        "com.unboundid:unboundid-ldapsdk:%s" % UNBOUNDID_LDAP_SDK_VERSION,
        "org.yaml:snakeyaml:%s" % SNAKE_YAML_VERSION,
        "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:%s" % JACKSON_VERSION,
        "com.fasterxml.jackson.core:jackson-databind:%s" % JACKSON_DATABIND_VERSION,
        "com.sun.mail:mailapi:%s" % JAVA_MAIL_VERSION,
        "com.sun.mail:smtp:%s" % JAVA_MAIL_VERSION,
        "org.apache.commons:commons-email:%s" % COMMONS_EMAIL_VERSION,

        # google
        "com.google.api-client:google-api-client:%s" % GOOGLE_API_CLIENT_VERSION,
        "com.google.apis:google-api-services-admin-directory:%s" % GOOGLE_DIRECTORY_SERVICE_VERSION,
        "com.google.auth:google-auth-library-oauth2-http:%s" % GOOGLE_OAUTH_HTTP_VERSION,
        "org.junit.jupiter:junit-jupiter:%s" % JUNIT_VERSION,
    ],
    excluded_artifacts = [
        "org.apache.httpcomponents:httpclient",
        "org.apache.httpcomponents:httpcore",
        "com.sun.mail:javax.mail",
        "javax.activation:activation",
    ],
    fetch_sources = True,
    maven_install_json = "//:maven_install.json",
    repositories = [
        "https://repo1.maven.org/maven2",
    ],
)

load("@maven//:defs.bzl", "pinned_maven_install")

pinned_maven_install()
