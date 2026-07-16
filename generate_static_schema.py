import os
import re

directories = [
    'services/admin-service/src/main/java/com/prayerlink/admin/model',
    'services/group-service/src/main/java/com/prayerlink/group/model',
    'services/identity-service/src/main/java/com/prayerlink/identity/model',
    'services/prayer-service/src/main/java/com/prayerlink/prayer/model'
]

def capitalize(s):
    return s[0].upper() + s[1:]

for d in directories:
    if not os.path.exists(d): continue
    for filename in os.listdir(d):
        if not filename.endswith('.java'): continue
        filepath = os.path.join(d, filename)
        with open(filepath, 'r') as f:
            content = f.read()

        class_match = re.search(r'public class (\w+)', content)
        if not class_match: continue
        class_name = class_match.group(1)

        # Extract fields
        fields = re.findall(r'private ([\w<>\s]+) (\w+);', content)
        
        # Extract getter annotations
        getters = {} # field -> annotations string
        for f_type, f_name in fields:
            f_type = f_type.strip()
            getter_name = 'get' + capitalize(f_name)
            # Find the getter method block
            getter_regex = r'((?:@\w+(?:\([^)]+\))?\s+)*)public\s+' + re.escape(f_type) + r'\s+' + getter_name + r'\s*\(\)'
            match = re.search(getter_regex, content)
            if match:
                getters[f_name] = match.group(1)
            else:
                getters[f_name] = ""

        # Build schema code
        schema_builder = []
        schema_builder.append(f"  public static final TableSchema<{class_name}> SCHEMA = StaticTableSchema.builder({class_name}.class)")
        schema_builder.append(f"    .newItemSupplier({class_name}::new)")
        
        for f_type, f_name in fields:
            f_type = f_type.strip()
            
            # Type class literal handling
            type_class = f_type
            if 'Set<' in f_type:
                # e.g. Set<String> -> EnhancedType.setOf(String.class)
                inner = re.search(r'Set<(\w+)>', f_type).group(1)
                type_literal = f"software.amazon.awssdk.enhanced.dynamodb.EnhancedType.setOf({inner}.class)"
            else:
                type_literal = f"{f_type}.class"
            
            tags = []
            anns = getters[f_name]
            if '@DynamoDbPartitionKey' in anns:
                tags.append("primaryPartitionKey()")
            if '@DynamoDbSortKey' in anns:
                tags.append("primarySortKey()")
            
            # Secondary Partition Key
            sec_pk = re.search(r'@DynamoDbSecondaryPartitionKey\s*\(\s*indexNames\s*=\s*(?:\{?"([^"]+)"\}?|"(.*?)")\s*\)', anns)
            if sec_pk:
                idx = sec_pk.group(1) or sec_pk.group(2)
                tags.append(f'secondaryPartitionKey("{idx}")')
            
            # Secondary Sort Key
            sec_sk = re.search(r'@DynamoDbSecondarySortKey\s*\(\s*indexNames\s*=\s*\{([^}]+)\}\s*\)', anns)
            if sec_sk:
                indices = [i.strip().strip('"') for i in sec_sk.group(1).split(',')]
                for idx in indices:
                    tags.append(f'secondarySortKey("{idx}")')
            else:
                sec_sk_single = re.search(r'@DynamoDbSecondarySortKey\s*\(\s*indexNames\s*=\s*"([^"]+)"\s*\)', anns)
                if sec_sk_single:
                    tags.append(f'secondarySortKey("{sec_sk_single.group(1)}")')

            tags_str = ""
            if tags:
                tags_str = f"\n      .tags({', '.join(tags)})"

            attr = f'    .addAttribute({type_literal}, a -> a.name("{f_name}")\n      .getter({class_name}::get{capitalize(f_name)})\n      .setter({class_name}::set{capitalize(f_name)}){tags_str})'
            schema_builder.append(attr)

        schema_builder.append("    .build();")
        
        schema_code = "\n".join(schema_builder)

        # Insert schema code before the first method or end of class
        # Replace @DynamoDbBean with the necessary imports and schema code?
        imports = [
            "import software.amazon.awssdk.enhanced.dynamodb.TableSchema;",
            "import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;",
            "import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.*;"
        ]
        
        if "StaticTableSchema" not in content:
            content = content.replace("package com.prayerlink", "\n".join(imports) + "\npackage com.prayerlink", 1)
            # Insert schema inside class
            class_idx = content.find('{', content.find(f'public class {class_name}'))
            content = content[:class_idx+1] + '\n\n' + schema_code + '\n' + content[class_idx+1:]
        
        with open(filepath, 'w') as f:
            f.write(content)

