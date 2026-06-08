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

# Align ELF PT_LOAD segments to 16KB pages for ALL targets below, including whisper.cpp's own
# ggml/whisper shared libs. Must precede add_subdirectory so the subproject inherits it. On the
# Pixel 8 / Android 16 (16KB-page kernel) a 4KB-aligned libggml-cpu.so SIGSEGVs in mul_mat.
# (Belt-and-suspenders alongside -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON in build.gradle.kts.)
add_link_options("-Wl,-z,max-page-size=16384")

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
