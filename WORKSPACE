workspace(name = "fastfilter_java")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

# JVM rules for Java compilation and testing
RULES_JVM_EXTERNAL_TAG = "6.5"
RULES_JVM_EXTERNAL_SHA = "08ea921df02ffe9924123b0686dc04fd0ff875710bfadb7ad42badb931b0fd50"

http_archive(
    name = "rules_jvm_external",
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    sha256 = RULES_JVM_EXTERNAL_SHA,
    url = "https://github.com/bazelbuild/rules_jvm_external/releases/download/%s/rules_jvm_external-%s.tar.gz" % (RULES_JVM_EXTERNAL_TAG, RULES_JVM_EXTERNAL_TAG),
)

load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")
rules_jvm_external_deps()

load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")
rules_jvm_external_setup()

# Java toolchain for JDK 24
load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = [
        # JUnit 5
        "org.junit.jupiter:junit-jupiter-api:5.11.0",
        "org.junit.jupiter:junit-jupiter-engine:5.11.0",
        "org.junit.jupiter:junit-jupiter-params:5.11.0",
        "org.junit.platform:junit-platform-runner:1.11.0",
        "org.junit.platform:junit-platform-launcher:1.11.0",
        
        # JMH
        "org.openjdk.jmh:jmh-core:1.37",
        "org.openjdk.jmh:jmh-generator-annprocess:1.37",
        
        # Test utilities
        "org.apiguardian:apiguardian-api:1.1.2",
    ],
    repositories = [
        "https://repo1.maven.org/maven2",
        "https://oss.sonatype.org/content/repositories/snapshots",
    ],
)

# Rules for CC (C++) compilation
http_archive(
    name = "rules_cc",
    sha256 = "2037875b9a4456dce4a79d112a8ae885bbc4aad968e6587dca6e64f3a0900cdf",
    strip_prefix = "rules_cc-0.0.9",
    urls = ["https://github.com/bazelbuild/rules_cc/releases/download/0.0.9/rules_cc-0.0.9.tar.gz"],
)

# Platform detection
http_archive(
    name = "platforms",
    sha256 = "8150406605389ececb6da07cbcb509d5637a3ab9a24bc69b1101531367d89d74",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/platforms/releases/download/0.0.10/platforms-0.0.10.tar.gz",
        "https://github.com/bazelbuild/platforms/releases/download/0.0.10/platforms-0.0.10.tar.gz",
    ],
)

# Fastfilter CPP integration as external repository
git_repository(
    name = "fastfilter_cpp",
    remote = "https://github.com/FastFilter/fastfilter_cpp.git",
    branch = "master",
)