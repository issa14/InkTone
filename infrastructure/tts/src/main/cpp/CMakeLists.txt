cmake_minimum_required(VERSION 3.18)
project(inktone_ctc_fbank CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(BUILD_SHARED_LIBS OFF)

include(FetchContent)

# Meme version que le prototype Python deja valide (Tache 5.2,
# PROTOTYPE_ALIGNEMENT_CTC.md §6.1) - pas une version devinee.
set(kaldi_native_fbank_URL "https://github.com/csukuangfj/kaldi-native-fbank/archive/refs/tags/v1.22.3.tar.gz")
set(kaldi_native_fbank_HASH "SHA256=9176cc66fc7ce1edf85cf355b06e320c57db6297df74277f575183468893cf61")

set(KALDI_NATIVE_FBANK_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(KALDI_NATIVE_FBANK_BUILD_PYTHON OFF CACHE BOOL "" FORCE)
set(KALDI_NATIVE_FBANK_ENABLE_CHECK OFF CACHE BOOL "" FORCE)

FetchContent_Declare(kaldi_native_fbank
  URL ${kaldi_native_fbank_URL}
  URL_HASH ${kaldi_native_fbank_HASH}
)
FetchContent_MakeAvailable(kaldi_native_fbank)

set_target_properties(kaldi-native-fbank-core PROPERTIES
  POSITION_INDEPENDENT_CODE ON
)

add_library(inktone_ctc_fbank SHARED fbank_jni.cpp)
target_include_directories(inktone_ctc_fbank PRIVATE ${kaldi_native_fbank_SOURCE_DIR})
target_link_libraries(inktone_ctc_fbank PRIVATE kaldi-native-fbank-core log)
