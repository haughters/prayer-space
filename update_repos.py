import os

repo_files = [
    'services/prayer-service/src/main/java/com/prayerlink/prayer/repository/PrayerRepository.java',
    'services/prayer-service/src/main/java/com/prayerlink/prayer/repository/PrayerUpdateRepository.java'
]

for filepath in repo_files:
    with open(filepath, 'r') as f:
        content = f.read()

    if 'static final TableSchema' not in content:
        model_name = 'Prayer' if 'PrayerRepository.java' in filepath else 'PrayerUpdate'
        schema_line = f"  public static final TableSchema<{model_name}> SCHEMA = TableSchema.fromBean({model_name}.class);"
        
        # Insert inside class definition
        content = content.replace('public class', f'public class', 1)
        # Find start of class
        class_idx = content.find('{', content.find('public class'))
        content = content[:class_idx+1] + '\n' + schema_line + content[class_idx+1:]
        
        # Replace TableSchema.fromBean(...) with SCHEMA
        content = content.replace(f"TableSchema.fromBean({model_name}.class)", 'SCHEMA')
        
        with open(filepath, 'w') as f:
            f.write(content)

