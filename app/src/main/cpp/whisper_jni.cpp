#include <jni.h>
#include <string>
#include <vector>
#include "whisper.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_freellm_chat_data_dictation_WhisperNative_initContext(JNIEnv *env, jobject, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    if (p == nullptr) return 0;
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_freellm_chat_data_dictation_WhisperNative_freeContext(JNIEnv *, jobject, jlong handle) {
    whisper_context *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx != nullptr) whisper_free(ctx);
}

JNIEXPORT jbyteArray JNICALL
Java_com_freellm_chat_data_dictation_WhisperNative_transcribe(JNIEnv *env, jobject, jlong handle,
                                                              jfloatArray samples, jstring lang,
                                                              jint threads) {
    whisper_context *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr) return env->NewByteArray(0);

    jsize n = env->GetArrayLength(samples);
    std::vector<float> pcm(static_cast<size_t>(n));
    env->GetFloatArrayRegion(samples, 0, n, pcm.data());

    const char *lp = env->GetStringUTFChars(lang, nullptr);
    std::string language = lp != nullptr ? lp : "auto";
    if (lp != nullptr) env->ReleaseStringUTFChars(lang, lp);
    if (language.empty()) language = "auto";

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    params.suppress_blank = true;
    params.n_threads = threads > 0 ? threads : 2;
    params.language = language.c_str();
    params.detect_language = false;

    std::string out;
    if (whisper_full(ctx, params, pcm.data(), static_cast<int>(pcm.size())) == 0) {
        int segs = whisper_full_n_segments(ctx);
        for (int i = 0; i < segs; i++) {
            const char *t = whisper_full_get_segment_text(ctx, i);
            if (t != nullptr) out += t;
        }
    }

    jbyteArray arr = env->NewByteArray(static_cast<jsize>(out.size()));
    if (arr != nullptr && !out.empty()) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(out.size()),
                                reinterpret_cast<const jbyte *>(out.data()));
    }
    return arr;
}

}
