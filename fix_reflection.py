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
        
        # Find all TableSchema.fromBean(Model.class)
        models = re.findall(r'TableSchema\.fromBean\(([A-Za-z0-9_]+)\.class\)', content)
        if not models: continue
        
        # Unique models
        models = list(set(models))
        model_classes = ', '.join([f'{m}.class' for m in models])
        annotation = f'@RegisterReflectionForBinding({{{model_classes}}})'
        
        if 'RegisterReflectionForBinding' in content: continue
        
        # Add import
        content = content.replace('import org.springframework.stereotype.Repository;', 
                                  'import org.springframework.stereotype.Repository;\nimport org.springframework.aot.hint.annotation.RegisterReflectionForBinding;')
        
        # Add annotation before @Repository
        content = content.replace('@Repository\npublic class', f'@Repository\n{annotation}\npublic class')
        
        with open(filepath, 'w') as f:
            f.write(content)

