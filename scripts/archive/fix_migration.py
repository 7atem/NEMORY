import re

with open(r'd:\Vault Brain\core\database\src\main\java\com\vaultbrain\core\database\VaultDatabaseMigrations.kt', 'r', encoding='utf-8') as f:
    content = f.read()

old_block = """            db.execSQL("CREATE INDEX IF NOT EXISTS `index_relationships_targetItemId` ON `relationships` (`targetItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_relationships_type` ON `relationships` (`type`)")
        }
    }"""

new_block = """            db.execSQL("CREATE INDEX IF NOT EXISTS `index_relationships_sourceItemId` ON `relationships` (`sourceItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_relationships_targetItemId` ON `relationships` (`targetItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_relationships_type` ON `relationships` (`type`)")
        }
    }"""

content = content.replace(old_block, new_block)

with open(r'd:\Vault Brain\core\database\src\main\java\com\vaultbrain\core\database\VaultDatabaseMigrations.kt', 'w', encoding='utf-8') as f:
    f.write(content)
