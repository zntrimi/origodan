cmake_minimum_required(VERSION 3.22.1)
project(mirror_latinime LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

file(GLOB_RECURSE LATINIME_CORE_SOURCES CONFIGURE_DEPENDS
        "${CMAKE_CURRENT_SOURCE_DIR}/aosp/*.cpp")

add_library(mirror_latinime SHARED
        latinime_bridge.cpp
        ${LATINIME_CORE_SOURCES})

target_include_directories(mirror_latinime PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/aosp")

target_compile_definitions(mirror_latinime PRIVATE __ANDROID__)
target_compile_options(mirror_latinime PRIVATE
        -O3
        -fvisibility=hidden
        -Wno-unused-parameter
        -Wno-unused-function
        -Wno-deprecated-declarations)

find_library(log-lib log)
target_link_libraries(mirror_latinime PRIVATE ${log-lib})
