project(common NONE)

# build pki-certsrv
javac(pki-certsrv-classes
    SOURCES
        src/main/java/*.java
    CLASSPATH
        ${SLF4J_API_JAR}
        ${LDAPJDK_JAR} ${SERVLET_JAR} ${XALAN_JAR} ${XERCES_JAR}
        ${JSS_JAR} ${COMMONS_CODEC_JAR} ${COMMONS_IO_JAR}
        ${COMMONS_LANG3_JAR} ${COMMONS_CLI_JAR}
        ${TOMCAT_CATALINA_JAR} ${TOMCAT_UTIL_JAR} ${SYMKEY_JAR}
        ${JACKSON2_ANNOTATIONS_JAR} ${JACKSON2_JAXB_ANNOTATIONS_JAR} ${JACKSON2_CORE_JAR} ${JACKSON2_DATABIND_JAR}
        ${JAXRS_API_JAR} ${RESTEASY_JAXRS_JAR} ${RESTEASY_ATOM_PROVIDER_JAR} ${RESTEASY_CLIENT_JAR}
        ${HTTPCLIENT_JAR} ${HTTPCORE_JAR}
        ${PKI_CMSUTIL_JAR} ${JAXB_API_JAR}
    OUTPUT_DIR
        ${CMAKE_CURRENT_BINARY_DIR}/classes
    DEPENDS
        pki-cmsutil-jar
)

configure_file(
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/resources/META-INF/MANIFEST.MF
    ${CMAKE_CURRENT_BINARY_DIR}/MANIFEST.MF
)

# Customize default pki.conf.
configure_file(
    ${CMAKE_CURRENT_SOURCE_DIR}/share/etc/pki.conf
    ${CMAKE_CURRENT_BINARY_DIR}/share/etc/pki.conf
)

jar(pki-certsrv-jar
    CREATE
        ${CMAKE_BINARY_DIR}/dist/pki-certsrv.jar
    OPTIONS
        m
    PARAMS
        ${CMAKE_CURRENT_BINARY_DIR}/MANIFEST.MF
    INPUT_DIR
        ${CMAKE_CURRENT_BINARY_DIR}/classes
    DEPENDS
        pki-certsrv-classes
)

set(PKI_CERTSRV_JAR ${CMAKE_BINARY_DIR}/dist/pki-certsrv.jar CACHE INTERNAL "pki-certsrv jar file")

# Create /usr/share/pki/lib. This can be customized for different platforms in RPM spec.

add_custom_target(pki-lib ALL
    COMMENT "Creating links for PKI common library")

add_custom_command(
    TARGET pki-lib
    COMMAND ${CMAKE_COMMAND} -E make_directory lib
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${COMMONS_CLI_JAR} lib/commons-cli.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${COMMONS_CODEC_JAR} lib/commons-codec.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${COMMONS_IO_JAR} lib/commons-io.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${COMMONS_LANG3_JAR} lib/commons-lang3.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${COMMONS_LOGGING_JAR} lib/commons-logging.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${HTTPCLIENT_JAR} lib/httpclient.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${HTTPCORE_JAR} lib/httpcore.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JACKSON2_CORE_JAR} lib/jackson-core.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JACKSON2_DATABIND_JAR} lib/jackson-databind.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JACKSON2_ANNOTATIONS_JAR} lib/jackson-annotations.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JACKSON2_JAXRS_BASE_JAR} lib/jackson-jaxrs-base.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JACKSON2_JAXRS_JSON_PROVIDER_JAR} lib/jackson-jaxrs-json-provider.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JACKSON2_JAXB_ANNOTATIONS_JAR} lib/jackson-module-jaxb-annotations.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JSS_JAR} lib/jss.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${LDAPJDK_JAR} lib/ldapjdk.jar
    COMMAND ln -sf /usr/share/java/pki/pki-certsrv.jar ${CMAKE_CURRENT_BINARY_DIR}/lib/pki-certsrv.jar
    COMMAND ln -sf /usr/share/java/pki/pki-cmsutil.jar ${CMAKE_CURRENT_BINARY_DIR}/lib/pki-cmsutil.jar
    COMMAND ln -sf /usr/share/java/pki/pki-tools.jar ${CMAKE_CURRENT_BINARY_DIR}/lib/pki-tools.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${RESTEASY_CLIENT_JAR} lib/resteasy-client.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${RESTEASY_LIB}/resteasy-jackson2-provider.jar lib/resteasy-jackson2-provider.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${RESTEASY_LIB}/resteasy-jaxb-provider.jar lib/resteasy-jaxb-provider.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${JAXRS_API_JAR} lib/jaxrs-api.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${RESTEASY_JAXRS_JAR} lib/resteasy-jaxrs.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${SERVLET_JAR} lib/servlet.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${SLF4J_API_JAR} lib/slf4j-api.jar
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${SLF4J_JDK14_JAR} lib/slf4j-jdk14.jar
    COMMAND ln -sf ${P11_KIT_TRUST} lib/p11-kit-trust.so
    COMMAND ${CMAKE_COMMAND} -E create_symlink ${COMMONS_NET_JAR} lib/commons-net.jar
)

if(JAVA_VERSION GREATER 10)
    add_custom_target(pki-java11plus-lib ALL
        DEPENDS pki-lib
        COMMENT "Creating links for library required in Java 11+")

    add_custom_command(
        TARGET pki-java11plus-lib
        COMMAND ${CMAKE_COMMAND} -E create_symlink ${JAXB_API_JAR} lib/jaxb-api.jar
        COMMAND ${CMAKE_COMMAND} -E create_symlink ${JAXB_IMPL_JAR} lib/jaxb-impl.jar
        COMMAND ${CMAKE_COMMAND} -E create_symlink ${JAKARTA_ACTIVATION_JAR} lib/jakarta.activation.jar
    )
endif(JAVA_VERSION GREATER 10)

add_custom_target(pki-man ALL
    COMMENT "Creating PKI manuals")

add_custom_command(
    TARGET pki-man
    COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_CURRENT_BINARY_DIR}/man/man5
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man5/pki-logging.5.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man5/pki-logging.5
    COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_CURRENT_BINARY_DIR}/man/man8
    COMMAND go-md2man -in ${CMAKE_SOURCE_DIR}/docs/manuals/man8/pki-upgrade.8.md -out ${CMAKE_CURRENT_BINARY_DIR}/man/man8/pki-upgrade.8
)

install(
    FILES
        ${CMAKE_BINARY_DIR}/dist/pki-certsrv.jar
    DESTINATION
        ${JAVA_JAR_INSTALL_DIR}/pki
)

install(
    DIRECTORY
        ${CMAKE_CURRENT_BINARY_DIR}/lib/
    DESTINATION
        ${DATA_INSTALL_DIR}/lib
)

install(
    FILES
        ${CMAKE_CURRENT_SOURCE_DIR}/share/etc/logging.properties
        ${CMAKE_CURRENT_BINARY_DIR}/share/etc/pki.conf
    DESTINATION
        ${DATA_INSTALL_DIR}/etc/
)

install(
    FILES
        ${CMAKE_CURRENT_SOURCE_DIR}/etc/pki.conf
    DESTINATION
        ${SYSCONF_INSTALL_DIR}/pki/
)

# install executables
install(
    FILES
        ${CMAKE_CURRENT_SOURCE_DIR}/sbin/pki-upgrade
    DESTINATION
        ${SBIN_INSTALL_DIR}
    PERMISSIONS
        OWNER_EXECUTE OWNER_WRITE OWNER_READ
        GROUP_EXECUTE GROUP_READ
        WORLD_EXECUTE WORLD_READ
)

install(
    FILES
        scripts/config
    DESTINATION
        ${DATA_INSTALL_DIR}/scripts/
    PERMISSIONS
        OWNER_WRITE OWNER_READ
        GROUP_READ
        WORLD_READ
)

install(
    DIRECTORY
        upgrade
    DESTINATION
        ${DATA_INSTALL_DIR}/
    FILE_PERMISSIONS
        OWNER_WRITE OWNER_READ
        GROUP_READ
        WORLD_READ
    PATTERN ".gitignore" EXCLUDE
)

install(
    DIRECTORY
        key
    DESTINATION
        ${DATA_INSTALL_DIR}/
    FILE_PERMISSIONS
        OWNER_READ
        GROUP_READ
        WORLD_READ
)

# install empty directories
install(
    DIRECTORY
    DESTINATION
        ${VAR_INSTALL_DIR}/log/pki
)

install(
    DIRECTORY
    DESTINATION
        ${VAR_INSTALL_DIR}/lock/pki/tomcat
)

install(
    DIRECTORY
    DESTINATION
        ${VAR_INSTALL_DIR}/run/pki/tomcat
)

install(
    DIRECTORY
    DESTINATION
        ${SYSTEMD_ETC_INSTALL_DIR}/pki-tomcatd.target.wants
)

install(
    DIRECTORY
    DESTINATION
        ${SYSTEMD_ETC_INSTALL_DIR}/pki-tomcatd-nuxwdog.target.wants
)

install(
    DIRECTORY
    DESTINATION
        /etc/sysconfig/pki
)

install(
    DIRECTORY
    DESTINATION
        /etc/sysconfig/pki/tomcat
)

# install man pages generated by md2man
install(
    DIRECTORY
        ${CMAKE_CURRENT_BINARY_DIR}/man/
    DESTINATION
        ${MAN_INSTALL_DIR}
    FILE_PERMISSIONS
        OWNER_READ OWNER_WRITE
        GROUP_READ
        WORLD_READ
)

add_subdirectory(examples)
add_subdirectory(python)
