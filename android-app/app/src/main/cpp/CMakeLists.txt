cmake_minimum_required(VERSION 3.22.1)
project(samanbridge C)

add_library(aether SHARED IMPORTED)
set_target_properties(aether PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libaether.so"
    IMPORTED_NO_SONAME TRUE
)

add_library(zeptun STATIC IMPORTED)
set_target_properties(zeptun PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/zeptun/zig-out/android/prebuilt/${ANDROID_ABI}/libzeptun.a"
    INTERFACE_INCLUDE_DIRECTORIES "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/zeptun/include"
)

add_library(samanbridge SHARED native_bridge.c)
add_library(zeptun-jni SHARED zeptun_jni.c)
target_include_directories(zeptun-jni PRIVATE ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/zeptun/include)

target_link_libraries(samanbridge aether log)
target_link_libraries(zeptun-jni zeptun log)
