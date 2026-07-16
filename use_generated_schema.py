import os
import re

directories = [
    'services/admin-service/src/main/java/com/prayerlink/admin/repository',
    'services/group-service/src/main/java/com/prayerlink/group/repository',
    'services/identity-service/src/main/java/com/prayerlink/identity/repository',
    'services/prayer-service/src/main/java/com/prayerlink/prayer/repository'
]

for d in directories:
    if not os.path.exists(d): continue
    for filename in os.listdir(d):
        if not filename.endswith('.java'): continue
        filepath = os.path.join(d, filename)
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Remove the static TableSchema declarations we previously added
        # public static final TableSchema<Prayer> PRAYER_SCHEMA = TableSchema.fromBean(Prayer.class);
        content = re.sub(r'\s*public static final TableSchema<\w+>\s+\w+_SCHEMA\s*=\s*TableSchema\.fromBean\(\w+\.class\);', '', content)
        
        # Replace occurrences of PRAYER_SCHEMA with Prayer.SCHEMA
        # But wait, earlier we replaced TableSchema.fromBean(M.class) with M_SCHEMA.
        # Let's just find any variables ending in _SCHEMA and replace them with the Model name + .SCHEMA.
        
        # Actually, let's just use regex to find where we pass it:
        # enhancedClient.table(tableNameResolver.resolve("Prayers"), PRAYER_SCHEMA)
        # Find all M_SCHEMA
        schemas = re.findall(r'([A-Z_]+)_SCHEMA', content)
        for s in set(schemas):
            model_name = ''.join([part.capitalize() for part in s.split('_')])
            content = content.replace(f"{s}_SCHEMA", f"{model_name}.SCHEMA")
            
        with open(filepath, 'w') as f:
            f.write(content)

