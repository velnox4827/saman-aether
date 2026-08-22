cmake_minimum_required(VERSION 3.22.1)
project(samanbridge C)

add_library(aether SHARED IMPORTED)
set_target_properties(aether PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libaether.so"
    IMPORTED_NO_SONAME TRUE
)

add_library(samanbridge SHARED native_bridge.c)

target_link_libraries(
    samanbridge
    aether
    log
)
