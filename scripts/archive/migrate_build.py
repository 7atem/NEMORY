import os
import re

for root, dirs, files in os.walk('.'):
    if 'build' in root or '.gradle' in root:
        continue
    for file in files:
        if file == 'build.gradle.kts':
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Remove kotlinOptions
            content = re.sub(r'\s*kotlinOptions\s*\{\s*jvmTarget\s*=\s*"17"\s*\}', '', content)
            
            # Add kotlin compiler options if needed
            if 'alias(libs.plugins.kotlin.android)' in content and 'compilerOptions' not in content:
                content += '\nkotlin {\n    compilerOptions {\n        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)\n    }\n}\n'
                
            # Replace android { block depending on type
            if 'alias(libs.plugins.android.application)' in content:
                content = re.sub(r'^android\s*\{', 'configure<com.android.build.api.dsl.ApplicationExtension> {', content, flags=re.MULTILINE)
            elif 'alias(libs.plugins.android.library)' in content:
                content = re.sub(r'^android\s*\{', 'configure<com.android.build.api.dsl.LibraryExtension> {', content, flags=re.MULTILINE)
                
            with open(path, 'w', encoding='utf-8') as f:
                f.write(content)
