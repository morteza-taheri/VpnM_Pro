cmake_minimum_required(VERSION 3.22.1)
project("softetherclient")

set(CMAKE_C_STANDARD 11)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(ENABLE_PROGRAMS OFF CACHE BOOL "Build mbed TLS programs" FORCE)
set(ENABLE_TESTING OFF CACHE BOOL "Build mbed TLS tests" FORCE)
set(USE_STATIC_MBEDTLS_LIBRARY ON CACHE BOOL "Build mbed TLS static library" FORCE)
set(USE_SHARED_MBEDTLS_LIBRARY OFF CACHE BOOL "Build mbed TLS shared library" FORCE)

set(MBEDTLS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/mbedtls")
if(IS_DIRECTORY "${MBEDTLS_DIR}")
    message(STATUS "Directory ${MBEDTLS_DIR} exists")
    if(EXISTS "${MBEDTLS_DIR}/CMakeLists.txt")
        message(STATUS "Found mbedtls/CMakeLists.txt")
    else()
        message(WARNING "mbedtls/CMakeLists.txt NOT found in ${MBEDTLS_DIR}")
        file(GLOB MBEDTLS_FILES "${MBEDTLS_DIR}/*")
        message(STATUS "Contents of ${MBEDTLS_DIR}: ${MBEDTLS_FILES}")
    endif()
else()
    message(FATAL_ERROR "mbedtls directory NOT found at ${MBEDTLS_DIR}")
endif()

add_subdirectory(mbedtls mbedtls_build)

# Find required libraries
find_library(log-lib log)
find_library(android-lib android)
find_library(z-lib z)

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/softether-core/include
    ${CMAKE_CURRENT_SOURCE_DIR}/mbedtls/include
)

# Global definitions for MbedTLS 3.x compatibility
add_compile_definitions(MBEDTLS_ALLOW_PRIVATE_ACCESS)
add_compile_definitions(MBEDTLS_MD_C)
add_compile_definitions(MBEDTLS_CIPHER_C)
add_compile_definitions(MBEDTLS_MD5_C)
add_compile_definitions(MBEDTLS_SHA1_C)
add_compile_definitions(MBEDTLS_SHA256_C)
add_compile_definitions(MBEDTLS_CHACHAPOLY_C)
add_compile_definitions(MBEDTLS_CHACHA20_C)
add_compile_definitions(MBEDTLS_POLY1305_C)
add_compile_definitions(MBEDTLS_CTR_DRBG_C)
add_compile_definitions(MBEDTLS_ENTROPY_C)
add_compile_definitions(MBEDTLS_SSL_TLS_C)
add_compile_definitions(MBEDTLS_SSL_CLI_C)
add_compile_definitions(MBEDTLS_NET_C)
add_compile_definitions(MBEDTLS_AES_C)
add_compile_definitions(MBEDTLS_BIGNUM_C)
add_compile_definitions(MBEDTLS_OID_C)
add_compile_definitions(MBEDTLS_PK_C)
add_compile_definitions(MBEDTLS_ASN1_PARSE_C)
add_compile_definitions(MBEDTLS_ASN1_WRITE_C)

# Source files for main library
set(SOFTETHER_SOURCES
    jni/softether_jni.c
    softether-core/src/crypto/crypto_mbedtls.c
    softether-core/src/proto/softether_protocol.c
    softether-core/src/proto/softether_rudp.c
    softether-core/src/crypto/compress.c
    softether-core/src/socket/tcp_socket.c
    softether-core/src/packet/packet_handler.c
    softether-core/src/net/dhcp_client.c
)

# Create main shared library
add_library(softether SHARED ${SOFTETHER_SOURCES})

# Link libraries for main library
target_link_libraries(softether
    mbedtls
    mbedx509
    mbedcrypto
    ${log-lib}
    ${android-lib}
    ${z-lib}
)

# Compiler flags
target_compile_options(softether PRIVATE
    -Wall
    -Wno-unused-parameter
    -Wno-unused-variable
    -fPIC
)

# Debug/Release specific flags
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(softether PRIVATE -g -O0)
else()
    target_compile_options(softether PRIVATE -O2)
endif()

# Test library sources
set(TEST_SOURCES
    test/native_test.c
    test/test_jni_bridge.c
    ${SOFTETHER_SOURCES}
)

# Create test shared library
add_library(softether_test SHARED ${TEST_SOURCES})

# Include directories for test library
target_include_directories(softether_test PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/softether-core/include
    ${CMAKE_CURRENT_SOURCE_DIR}/mbedtls/include
    ${CMAKE_CURRENT_SOURCE_DIR}/test
)

# Link libraries for test library
target_link_libraries(softether_test
    mbedtls
    mbedx509
    mbedcrypto
    ${log-lib}
    ${android-lib}
    ${z-lib}
)

# Compiler flags for test library
target_compile_options(softether_test PRIVATE
    -Wall
    -Wno-unused-parameter
    -Wno-unused-variable
    -fPIC
)

# Export compile commands for IDE support
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
