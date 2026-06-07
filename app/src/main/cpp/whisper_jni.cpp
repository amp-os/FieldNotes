// FieldNotes — whisper_jni.cpp
// Authored by: whisper module | Implements: 05_WHISPER_MODULE.md
// JNI bridge to whisper.cpp (v1.8.x). Fully offline transcription.
#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_fieldnotes_app_core_whisper_WhisperEngine_initContext(
        JNIEnv* env, jobject /* obj */, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false; // Android GPU not supported by whisper.cpp here
    whisper_context* ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (ctx == nullptr) {
        LOGE("whisper_init_from_file_with_params returned null");
    }
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_fieldnotes_app_core_whisper_WhisperEngine_transcribeAudio(
        JNIEnv* env, jobject /* obj */, jlong ctxPtr,
        jfloatArray audioData, jint numSamples) {
    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    if (ctx == nullptr) {
        return env->NewStringUTF("[Whisper not initialised]");
    }
    jfloat* samples = env->GetFloatArrayElements(audioData, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.language = "en";
    params.n_threads = 4; // Pixel 8 has 8 cores
    params.single_segment = false;
    params.token_timestamps = false;

    int result = whisper_full(ctx, params, samples, numSamples);
    env->ReleaseFloatArrayElements(audioData, samples, JNI_ABORT);

    if (result != 0) {
        LOGE("whisper_full failed: %d", result);
        return env->NewStringUTF("[Transcription failed]");
    }

    std::string output;
    const int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        output += whisper_full_get_segment_text(ctx, i);
        if (i < n_segments - 1) output += " ";
    }
    return env->NewStringUTF(output.c_str());
}

JNIEXPORT void JNICALL
Java_com_fieldnotes_app_core_whisper_WhisperEngine_freeContext(
        JNIEnv* /* env */, jobject /* obj */, jlong ctxPtr) {
    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    if (ctx) whisper_free(ctx);
}

} // extern "C"
