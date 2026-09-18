cmake_minimum_required(VERSION 3.22.1)
project(nemory_llama C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Disable unnecessary llama.cpp sub-targets for Android shared lib
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

include(FetchContent)
FetchContent_Declare(
    llamacpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        5ac847190e979e0da7c4a21806630805f396d487 # v0.4.0
)
FetchContent_MakeAvailable(llamacpp)
if(NOT TARGET mtmd)
    add_subdirectory(${llamacpp_SOURCE_DIR}/tools/mtmd ${llamacpp_BINARY_DIR}/tools/mtmd)
endif()

add_library(nemory_llama SHARED
    llama_bridge.cpp
)

target_include_directories(nemory_llama PRIVATE
    ${llamacpp_SOURCE_DIR}/include
    ${llamacpp_SOURCE_DIR}/ggml/include
    ${llamacpp_SOURCE_DIR}/tools/mtmd
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party
)

target_link_libraries(nemory_llama PRIVATE
    llama
    mtmd
    log
    android
)
