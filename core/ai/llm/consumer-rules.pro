# ProGuard rules for :core:ai:llm
-keep class com.vaultbrain.core.ai.llm.llama.LlamaBridge { *; }
-keep interface com.vaultbrain.core.ai.llm.llama.LlamaTokenCallback { *; }
-keepclassmembers class * implements com.vaultbrain.core.ai.llm.llama.LlamaTokenCallback {
    public boolean onToken(java.lang.String);
}
