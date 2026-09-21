# Nemory (Vault Brain) 🧠🔒

> **An intelligent, on-device personal vault for documents and life administration.**

![Nemory Banner](https://via.placeholder.com/800x200.png?text=Nemory+Personal+Vault)

Nemory is an advanced, privacy-first Android application designed to organize, extract, and search your personal documents and life admin data. Built with modern Android architecture and Kotlin Multiplatform, it features an on-device Retrieval-Augmented Generation (RAG) engine powered by local Large Language Models (LLMs) and Vision-Language Models (VLMs). 

Everything stays on your device. Zero cloud dependency.

## ✨ Key Features

*   **🔒 Privacy-First & Fail-Closed Security**: AES-256-GCM hardware-backed keystore encryption for media, SQLCipher for the database, and an isolated ObjectBox vector store. 
*   **🤖 On-Device AI & RAG**: Runs Qwen3-VL-2B, Gemini Nano, and Gemma 3 entirely on-device for categorization, insights, and natural language Q&A over your personal vault.
*   **📸 Unified Capture**: Ingest documents via Camera, Gallery, System Share Sheet, PDF paste, or Voice. Features deterministic ML Kit OCR with a Latin/Arabic pipeline.
*   **🛡️ Decoy Mode**: A specialized stealth mode with full storage isolation and plausible deniability.
*   **🌍 Bilingual Support**: First-class support for both English (LTR) and Arabic (RTL) across the UI, OCR, and AI prompting.
*   **📊 Proactive Intelligence**: Daily dynamic briefings based on your documents, using multi-factor scoring (relevance, confidence, urgency, novelty).

## 🏗️ Architecture & Tech Stack

Nemory is built with a modern, modular Kotlin architecture:
*   **UI**: Jetpack Compose & Compose Multiplatform
*   **Core**: Kotlin Multiplatform (`:shared` module for cross-platform logic)
*   **Dependency Injection**: Hilt / Dagger
*   **Persistence**: Room (Schema v20) + SQLCipher, ObjectBox (Vector Store)
*   **Background Work**: WorkManager (Deferred analysis, embeddings backfill)
*   **On-Device ML**: TFLite, ML Kit, llama.cpp JNI (`QwenLlmClient`)
*   **Image Loading**: Coil (with custom `VaultMediaFetcher` for encrypted streaming)

> For a deep dive into the architecture, check out our [Architecture Guide](AGENTS.md) and the [Master Documentation](NEMORY_MASTER_DOCUMENTATION.md).

## 🚀 Getting Started

### Prerequisites
*   Android Studio Ladybug (or newer)
*   JDK 17
*   Android SDK 36 (Min SDK 29)
*   NDK 27.2.12479018 & CMake 3.22.1 (for local LLM compilation)

### Building the Project
1. Clone the repository:
   ```bash
   git clone https://github.com/7atem/NEMORY.git
   cd NEMORY
   ```
2. Build the Android App:
   ```bash
   ./gradlew :app:assembleDebug
   ```
   *(Note: For test builds without native C++ LLM libraries, use `-Pnemory.skipNativeForTests=true`)*

3. Run Tests:
   ```bash
   ./gradlew testDebugUnitTest
   ```

## 📂 Module Structure

*   `:app` - The main Android application shell.
*   `:shared` - Kotlin Multiplatform module containing shared logic (iOS/Android).
*   `:feature:*` - Feature modules (e.g., `:feature:capture`, `:feature:brain`, `:feature:vault`).
*   `:core:*` - Core infrastructure (e.g., `:core:database`, `:core:ai`, `:core:security`).
*   `:sync:*` - External integration modules (e.g., `:sync:drive`).

## 📜 License

This project is licensed under the **Apache License 2.0** - see the [LICENSE](LICENSE) file for details.

---
*Developed by 7atem.*
