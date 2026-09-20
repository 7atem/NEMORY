import glob
import os

def replace_in_files(pattern, old_str, new_str):
    for filepath in glob.glob(pattern, recursive=True):
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            new_content = content.replace(old_str, new_str)
            if content != new_content:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(new_content)
        except Exception as e:
            print(f"Error {filepath}: {e}")

# 1. System.currentTimeMillis()
replace_in_files("shared/src/commonMain/kotlin/**/*.kt", "System.currentTimeMillis()", "com.vaultbrain.shared.util.currentTimeMillis()")

# 2. java.util.UUID.randomUUID().toString()
replace_in_files("shared/src/commonMain/kotlin/**/*.kt", "java.util.UUID.randomUUID().toString()", "com.vaultbrain.shared.util.randomUUIDString()")
replace_in_files("shared/src/commonMain/kotlin/**/*.kt", "UUID.randomUUID().toString()", "com.vaultbrain.shared.util.randomUUIDString()")
replace_in_files("shared/src/commonMain/kotlin/**/*.kt", "import java.util.UUID\n", "")

# 3. StringRes and DrawableRes
replace_in_files("shared/src/commonMain/kotlin/**/*.kt", "@StringRes ", "")
replace_in_files("shared/src/commonMain/kotlin/**/*.kt", "@DrawableRes ", "")
replace_in_files("shared/src/commonMain/kotlin/**/*.kt", "import androidx.annotation.StringRes\n", "")
replace_in_files("shared/src/commonMain/kotlin/**/*.kt", "import androidx.annotation.DrawableRes\n", "")

print("Fixed unresolved KMP references.")
