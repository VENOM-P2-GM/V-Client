cmake_minimum_required(VERSION 3.22.1)
project("vclient" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(vclient SHARED
    vclient_jni.cpp
    vclient/Runtime.cpp
    vclient/Logger.cpp
    vclient/CrashHandler.cpp
    vclient/FpsStats.cpp
    vclient/Bridge.cpp)

target_include_directories(vclient PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_compile_options(vclient PRIVATE
    -Wall -Wextra -Wno-unused-parameter
    -O2 -fvisibility=hidden -fexceptions -frtti
    -fstack-protector-strong)

find_library(log-lib log)
find_library(android-lib android)
target_link_libraries(vclient PRIVATE ${log-lib} ${android-lib})
