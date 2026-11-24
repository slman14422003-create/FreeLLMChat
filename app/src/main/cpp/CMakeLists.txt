cmake_minimum_required(VERSION 3.22.1)
project(freellm_whisper C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_FLAGS_DEBUG "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG")

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

if(DEFINED WHISPER_CPP_DIR AND EXISTS "${WHISPER_CPP_DIR}/CMakeLists.txt")
    add_subdirectory("${WHISPER_CPP_DIR}" whisper_cpp_build EXCLUDE_FROM_ALL)
else()
    include(FetchContent)
    FetchContent_Declare(
        whisper_cpp
        GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
        GIT_TAG v1.7.5
        GIT_SHALLOW TRUE
    )
    FetchContent_MakeAvailable(whisper_cpp)
endif()

add_library(freellm_whisper SHARED whisper_jni.cpp)
target_link_libraries(freellm_whisper whisper log)
target_link_options(freellm_whisper PRIVATE -Wl,-z,max-page-size=16384)
