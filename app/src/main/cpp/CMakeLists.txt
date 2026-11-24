cmake_minimum_required(VERSION 3.22.1)
project("adw_vdlms_engine" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(
    adw_vdlms_engine
    SHARED
    audio_engine.cpp
    dsp/gain.cpp
    dsp/eq.cpp
    dsp/crossover.cpp
    dsp/delay.cpp
    dsp/limiter.cpp
    dsp/DspChain.cpp
    jni/native_bridge.cpp
)

target_include_directories(
    adw_vdlms_engine
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(
    adw_vdlms_engine
    oboe::oboe
    log
    android
)
