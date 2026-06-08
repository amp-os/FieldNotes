# FieldNotes — app/src/main/cpp/CMakeLists.txt
# Authored by: whisper module | Implements: 03_SETUP_ENVIRONMENT.md / 05_WHISPER_MODULE.md
#
# Builds whisper.cpp (git submodule) and the JNI bridge. x86 SIMD is disabled via -DGGML_* flags
# from app/build.gradle.kts so the x86_64 emulator build links.
cmake_minimum_required(VERSION 3.22.1)
project("fieldnotes")

set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper.cpp)

add_library(
    fieldnotes-jni
    SHARED
    whisper_jni.cpp
)

target_include_directories(fieldnotes-jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/include)

target_link_libraries(
    fieldnotes-jni
    whisper
    android
    log
)

# Align ELF PT_LOAD segments to 16KB for kernels with 16KB page size (e.g. Pixel 8).
# Without this the dynamic linker uses compatibility mode, degrading memory throughput
# for whisper.cpp's matrix operations.
target_link_options(fieldnotes-jni PRIVATE "-Wl,-z,max-page-size=16384")
