import os
import shutil
import glob

def find_files(pattern):
    return glob.glob(pattern, recursive=True)

def replace_in_file(filepath, old_pkg, new_pkg):
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
    except:
        return
        
    new_content = content.replace(old_pkg, new_pkg)
    if content != new_content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)

def main():
    print("Starting KMP Phase 5 Migration script...")
    
    # 1. Move core/common/models to shared/src/commonMain/kotlin/com/vaultbrain/shared/model/
    src_model_dir = "core/common/src/main/java/com/vaultbrain/core/common/model"
    dst_model_dir = "shared/src/commonMain/kotlin/com/vaultbrain/shared/model"
    
    if os.path.exists(src_model_dir):
        os.makedirs(dst_model_dir, exist_ok=True)
        # Move all contents
        for item in os.listdir(src_model_dir):
            s = os.path.join(src_model_dir, item)
            d = os.path.join(dst_model_dir, item)
            if not os.path.exists(d):
                shutil.move(s, d)
        print("Moved models to shared.")
    
    # 2. Move core/database/entity to shared/src/commonMain/kotlin/com/vaultbrain/shared/database/entity/
    src_entity_dir = "core/database/src/main/java/com/vaultbrain/core/database/entity"
    dst_entity_dir = "shared/src/commonMain/kotlin/com/vaultbrain/shared/database/entity"
    
    if os.path.exists(src_entity_dir):
        os.makedirs(dst_entity_dir, exist_ok=True)
        # Move all contents
        for item in os.listdir(src_entity_dir):
            s = os.path.join(src_entity_dir, item)
            d = os.path.join(dst_entity_dir, item)
            if not os.path.exists(d):
                shutil.move(s, d)
        print("Moved entities to shared.")

    # 3. Move core/database/dao to shared/src/commonMain/kotlin/com/vaultbrain/shared/database/dao/
    src_dao_dir = "core/database/src/main/java/com/vaultbrain/core/database/dao"
    dst_dao_dir = "shared/src/commonMain/kotlin/com/vaultbrain/shared/database/dao"
    
    if os.path.exists(src_dao_dir):
        os.makedirs(dst_dao_dir, exist_ok=True)
        # Move all contents
        for item in os.listdir(src_dao_dir):
            s = os.path.join(src_dao_dir, item)
            d = os.path.join(dst_dao_dir, item)
            if not os.path.exists(d):
                shutil.move(s, d)
        print("Moved DAOs to shared.")

    # 4. Refactor imports across all kt files
    print("Refactoring package imports...")
    all_kt_files = find_files("**/*.kt")
    
    for f in all_kt_files:
        replace_in_file(f, "com.vaultbrain.core.common.model", "com.vaultbrain.shared.model")
        replace_in_file(f, "com.vaultbrain.core.database.entity", "com.vaultbrain.shared.database.entity")
        replace_in_file(f, "com.vaultbrain.core.database.dao", "com.vaultbrain.shared.database.dao")

    print("Phase 5 KMP Migration completed.")

if __name__ == '__main__':
    main()
