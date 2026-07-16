import os
import re

directories = [
    'services/admin-service/src/main/java/com/prayerlink/admin/repository',
    'services/group-service/src/main/java/com/prayerlink/group/repository',
    'services/identity-service/src/main/java/com/prayerlink/identity/repository'
]

for d in directories:
    if not os.path.exists(d): continue
    for filename in os.listdir(d):
        if not filename.endswith('.java'): continue
        filepath = os.path.join(d, filename)
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Find all TableSchema.fromBean(Model.class)
        matches = re.findall(r'TableSchema\.fromBean\(([A-Za-z0-9_]+)\.class\)', content)
        if not matches: continue
        
        models = list(set(matches))
        schemas = []
        for m in models:
            schema_name = f"{m.upper()}_SCHEMA"
            schema_line = f"  public static final TableSchema<{m}> {schema_name} = TableSchema.fromBean({m}.class);"
            schemas.append(schema_line)
            content = content.replace(f"TableSchema.fromBean({m}.class)", schema_name)
        
        schema_lines = '\n'.join(schemas)
        
        # Insert inside class definition
        content = content.replace('public class', f'public class', 1)
        class_idx = content.find('{', content.find('public class'))
        content = content[:class_idx+1] + '\n' + schema_lines + content[class_idx+1:]
        
        with open(filepath, 'w') as f:
            f.write(content)

