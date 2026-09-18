#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <memory>
#include <cstdlib>
#include <algorithm>
#include <codecvt>
#include <locale>
#include <atomic>

#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define TAG "LlamaBridgeNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct LlamaSession {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    mtmd_context* mtmd_ctx = nullptr;
    std::mutex session_mutex;
    std::atomic<bool> abort_flag{false};

    ~LlamaSession() {
        if (mtmd_ctx) {
            mtmd_free(mtmd_ctx);
            mtmd_ctx = nullptr;
        }
        if (ctx) {
            llama_free(ctx);
            ctx = nullptr;
        }
        if (model) {
            llama_free_model(model);
            model = nullptr;
        }
    }
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_vaultbrain_core_ai_llm_llama_LlamaBridge_nativeLoad(
    JNIEnv* env,
    jobject /* this */,
    jstring jModelPath,
    jstring jMmprojPath,
    jint ctxSize,
    jint nThreads
) {
    const char* model_path = env->GetStringUTFChars(jModelPath, nullptr);
    const char* mmproj_path = jMmprojPath ? env->GetStringUTFChars(jMmprojPath, nullptr) : nullptr;

    LOGI("Loading model from %s", model_path);
    if (mmproj_path) {
        LOGI("Loading mmproj from %s", mmproj_path);
    }

    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    llama_model* model = llama_load_model_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jModelPath, model_path);

    if (!model) {
        LOGE("Failed to load model from file");
        if (mmproj_path) env->ReleaseStringUTFChars(jMmprojPath, mmproj_path);
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = ctxSize > 0 ? ctxSize : 2048;
    ctx_params.n_threads = nThreads > 0 ? nThreads : 4;
    ctx_params.n_threads_batch = ctx_params.n_threads;

    llama_context* ctx = llama_new_context_with_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create llama context");
        llama_free_model(model);
        if (mmproj_path) env->ReleaseStringUTFChars(jMmprojPath, mmproj_path);
        return 0;
    }

    mtmd_context* mtmd_ctx = nullptr;
    if (mmproj_path && mmproj_path[0] != '\0') {
        mtmd_context_params mtmd_params = mtmd_context_params_default();
        mtmd_ctx = mtmd_init_from_file(mmproj_path, model, mtmd_params);
        env->ReleaseStringUTFChars(jMmprojPath, mmproj_path);
        if (!mtmd_ctx) {
            LOGE("Failed to load vision projector");
            llama_free(ctx);
            llama_free_model(model);
            return 0;
        } else {
            LOGI("Vision projector loaded successfully");
        }
    }

    auto* session = new LlamaSession();
    session->model = model;
    session->ctx = ctx;
    session->mtmd_ctx = mtmd_ctx;

    return reinterpret_cast<jlong>(session);
}

JNIEXPORT jstring JNICALL
Java_com_vaultbrain_core_ai_llm_llama_LlamaBridge_nativeGenerate(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jstring jPrompt,
    jbyteArray jJpegBytes,
    jint maxTokens,
    jfloat temperature,
    jint topK,
    jobject jCallback
) {
    auto* session = reinterpret_cast<LlamaSession*>(handle);
    if (!session || !session->ctx || !session->model) {
        LOGE("Invalid handle passed to nativeGenerate");
        return nullptr;
    }

    std::lock_guard<std::mutex> lock(session->session_mutex);

    const jchar* input_chars = env->GetStringChars(jPrompt, nullptr);
    std::u16string input16(reinterpret_cast<const char16_t*>(input_chars), env->GetStringLength(jPrompt));
    env->ReleaseStringChars(jPrompt, input_chars);
    std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t> converter;
    std::string user_prompt;
    try { user_prompt = converter.to_bytes(input16); } catch (...) { return nullptr; }
    if (jJpegBytes) {
        if (!session->mtmd_ctx) return nullptr;
        user_prompt = std::string(mtmd_default_marker()) + "\n" + user_prompt;
    }
    llama_chat_message message = {"user", user_prompt.c_str()};
    std::vector<char> formatted(user_prompt.size() + 1024);
    int length = llama_chat_apply_template("chatml", &message, 1, true, formatted.data(), formatted.size());
    if (length < 0) return nullptr;
    if (length > static_cast<int>(formatted.size())) {
        formatted.resize(length);
        length = llama_chat_apply_template("chatml", &message, 1, true, formatted.data(), formatted.size());
    }
    if (length <= 0) return nullptr;
    std::string prompt(formatted.data(), length);
    llama_memory_clear(llama_get_memory(session->ctx), true);
    llama_pos n_past = 0;

    jclass callbackClass = nullptr;
    jmethodID invokeMethod = nullptr;
    if (jCallback) {
        callbackClass = env->GetObjectClass(jCallback);
        invokeMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)Z");
    }

    std::string result_text;

    if (jJpegBytes != nullptr && session->mtmd_ctx != nullptr) {
        jsize bytes_len = env->GetArrayLength(jJpegBytes);
        jbyte* bytes = env->GetByteArrayElements(jJpegBytes, nullptr);

        int width = 0, height = 0, channels = 0;
        unsigned char* img_data = stbi_load_from_memory(
            reinterpret_cast<const unsigned char*>(bytes),
            bytes_len,
            &width,
            &height,
            &channels,
            3
        );
        env->ReleaseByteArrayElements(jJpegBytes, bytes, JNI_ABORT);

        if (img_data) {
            LOGI("Decoded image %dx%d (%d channels)", width, height, channels);
            mtmd_bitmap* bitmap = mtmd_bitmap_init(width, height, img_data);
            stbi_image_free(img_data);

            if (bitmap) {
                mtmd_input_text input = {prompt.c_str(), prompt.size(), true, true};
                mtmd_input_chunks* chunks = mtmd_input_chunks_init();
                const mtmd_bitmap* bitmaps[] = {bitmap};
                int status = mtmd_tokenize(session->mtmd_ctx, chunks, &input, bitmaps, 1);
                if (status == 0 && mtmd_helper_get_n_pos(chunks) + 32 < llama_n_ctx(session->ctx)) {
                    status = mtmd_helper_eval_chunks(session->mtmd_ctx, session->ctx, chunks,
                        0, 0, llama_n_batch(session->ctx), true, &n_past);
                } else { status = -1; }
                mtmd_input_chunks_free(chunks);
                mtmd_bitmap_free(bitmap);
                if (status != 0) return nullptr;
            }
        } else {
            LOGE("Failed to decode JPEG bytes with stb_image");
            return nullptr;
        }
    } else {
        const llama_vocab* vocab = llama_model_get_vocab(session->model);
        std::vector<llama_token> tokens(prompt.length() + 128);
        int n_tokens = llama_tokenize(vocab, prompt.c_str(), prompt.length(), tokens.data(), tokens.size(), true, true);
        if (n_tokens <= 0 || n_tokens + 32 >= static_cast<int>(llama_n_ctx(session->ctx))) return nullptr;
        tokens.resize(n_tokens);
        for (int offset = 0; offset < n_tokens; ) {
            if (jCallback && invokeMethod) {
                jstring empty = env->NewStringUTF("");
                const bool active = env->CallBooleanMethod(jCallback, invokeMethod, empty);
                env->DeleteLocalRef(empty);
                if (env->ExceptionCheck() || !active) return nullptr;
            }
            const int count = std::min(n_tokens - offset, 32);
            llama_batch batch = llama_batch_get_one(tokens.data() + offset, count);
            if (llama_decode(session->ctx, batch) != 0) return nullptr;
            offset += count;
        }
        n_past = n_tokens;
    }
    if (n_past <= 0) return nullptr;

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    if (temperature > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    }
    if (topK > 0) {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    }
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    int n_cur = 0;
    int max_gen = std::min(maxTokens > 0 ? maxTokens : 512, static_cast<int>(llama_n_ctx(session->ctx)) - n_past - 1);
    const llama_vocab* vocab = llama_model_get_vocab(session->model);

    while (n_cur < max_gen) {
        if (session->abort_flag.load()) {
            LOGI("Generation cancelled by nativeFree");
            break;
        }
        llama_token id = llama_sampler_sample(smpl, session->ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) {
            break;
        }

        char piece_buf[128];
        int n_chars = llama_token_to_piece(vocab, id, piece_buf, sizeof(piece_buf), 0, true);
        if (n_chars > 0) {
            std::string token_str(piece_buf, n_chars);
            result_text += token_str;

            if (jCallback && invokeMethod) {
                // A token can end mid-codepoint. Only publish complete cumulative UTF-16 text.
                std::u16string visible16;
                try { visible16 = converter.from_bytes(result_text); } catch (...) { }
                jstring jPiece = env->NewString(reinterpret_cast<const jchar*>(visible16.data()), visible16.size());
                jboolean keepGoing = env->CallBooleanMethod(jCallback, invokeMethod, jPiece);
                env->DeleteLocalRef(jPiece);
                if (env->ExceptionCheck() || !keepGoing) {
                    LOGI("Generation cancelled by Kotlin callback");
                    break;
                }
            }
        }

        llama_batch batch = llama_batch_get_one(&id, 1);
        if (llama_decode(session->ctx, batch) != 0) {
            LOGE("llama_decode failed");
            break;
        }

        n_cur++;
    }

    llama_sampler_free(smpl);

    if (env->ExceptionCheck()) return nullptr;
    try {
        const auto result16 = converter.from_bytes(result_text);
        return env->NewString(reinterpret_cast<const jchar*>(result16.data()), result16.size());
    } catch (...) { return nullptr; }
}

JNIEXPORT void JNICALL
Java_com_vaultbrain_core_ai_llm_llama_LlamaBridge_nativeFree(
    JNIEnv* env,
    jobject /* this */,
    jlong handle
) {
    auto* session = reinterpret_cast<LlamaSession*>(handle);
    if (session) {
        LOGI("Freeing LlamaSession handle");
        session->abort_flag = true;
        std::lock_guard<std::mutex> lock(session->session_mutex);
        delete session;
    }
}

} // extern "C"
