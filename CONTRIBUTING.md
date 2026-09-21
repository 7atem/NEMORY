# Contributing to Nemory

First off, thank you for considering contributing to Nemory! It's people like you that make open-source such a great community.

## 🧠 What is Nemory?
Nemory is a privacy-first, on-device personal vault built using **Kotlin Multiplatform** and **Jetpack Compose**. It runs complex **Local LLMs and VLMs (Vision-Language Models)** directly on Android devices without relying on cloud APIs.

## 🚀 Getting Started

### Prerequisites
*   **Android Studio Ladybug** (or newer).
*   **JDK 17**.
*   **NDK 27.2.12479018** & **CMake 3.22.1** (Required if you are compiling the native C++ inference engine for local models).

### Building the Project Locally

1. **Clone the repo:**
   ```bash
   git clone https://github.com/7atem/NEMORY.git
   cd NEMORY
   ```

2. **Build the Android App:**
   ```bash
   ./gradlew :app:assembleDebug
   ```
   > **Note:** Compiling the native llama.cpp JNI bindings can take a while. If you are only working on UI or standard Kotlin logic, you can skip native compilation by adding the flag: `-Pnemory.skipNativeForTests=true`

## 🛠️ How to Contribute

### 1. Find an Issue
Look for issues tagged with `good first issue` or `help wanted`. If you want to work on something else, please open an issue first to discuss it with the maintainers before writing code.

### 2. Branching
Create a new branch for your feature or bugfix:
```bash
git checkout -b feature/your-feature-name
# or
git checkout -b fix/your-bug-fix
```

### 3. Code Style & Architecture Guidelines
*   **Architecture**: We strictly follow the Android recommended architecture (UI Layer -> Domain Layer -> Data Layer). See [AGENTS.md](AGENTS.md) for a deep dive into the vault's internal structures, decoy mode, and RAG loops.
*   **Kotlin Multiplatform**: When writing business logic, try to place it in the `:shared` module whenever possible to maintain iOS/Android parity.
*   **Testing**: Add unit tests for any new heuristics, RAG parsers, or database migrations. Run tests locally using `./gradlew testDebugUnitTest`.

### 4. Commit Messages
We use Conventional Commits. Please prefix your commits with one of the following:
*   `feat:` A new feature
*   `fix:` A bug fix
*   `docs:` Documentation only changes
*   `refactor:` A code change that neither fixes a bug nor adds a feature
*   `test:` Adding missing tests or correcting existing tests

### 5. Open a Pull Request
Push your branch to your fork and open a Pull Request against the `main` (or `ios-kmp-foundation`) branch. Provide a clear description of the changes and any screenshots if you modified the UI.

## 💬 Community & Help
If you get stuck, feel free to open a Discussion on GitHub. We are happy to help you get the local models running and guide you through the Compose codebase!
