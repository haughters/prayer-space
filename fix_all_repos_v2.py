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
        
        # Add @RegisterReflectionForBinding
        models_for_reflection = re.findall(r'TableSchema\.fromBean\(([A-Za-z0-9_]+)\.class\)', content)
        if not models_for_reflection: continue
        
        models_for_reflection = list(set(models_for_reflection))
        model_classes = ', '.join([f'{m}.class' for m in models_for_reflection])
        annotation = f'@RegisterReflectionForBinding({{{model_classes}}})'
        
        if 'RegisterReflectionForBinding' not in content:
            content = content.replace('import org.springframework.stereotype.Repository;', 
                                      'import org.springframework.stereotype.Repository;\nimport org.springframework.aot.hint.annotation.RegisterReflectionForBinding;')
            content = content.replace('@Repository\npublic class', f'@Repository\n{annotation}\npublic class')
        
        # Now fix TableSchema to be static
        schemas = []
        for m in models_for_reflection:
            schema_name = f"{m.upper()}_SCHEMA"
            schema_line = f"  public static final TableSchema<{m}> {schema_name} = TableSchema.fromBean({m}.class);"
            schemas.append(schema_line)
        
        schema_lines = '\n'.join(schemas)
        
        # Insert inside class definition
        content = content.replace('public class', f'public class', 1)
        class_idx = content.find('{', content.find('public class'))
        content = content[:class_idx+1] + '\n' + schema_lines + content[class_idx+1:]
        
        # Replace occurrences of TableSchema.fromBean(M.class) with M_SCHEMA, EXCEPT the ones we just added!
        # Easiest way: replace them before we add the static fields, then prepend the static fields!
        # Let's re-read the file to do it cleaner.
        
