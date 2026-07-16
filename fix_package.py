import os

directories = [
    'services/admin-service/src/main/java/com/prayerlink/admin/model',
    'services/group-service/src/main/java/com/prayerlink/group/model',
    'services/identity-service/src/main/java/com/prayerlink/identity/model',
    'services/prayer-service/src/main/java/com/prayerlink/prayer/model'
]

for d in directories:
    if not os.path.exists(d): continue
    for filename in os.listdir(d):
        if not filename.endswith('.java'): continue
        filepath = os.path.join(d, filename)
        with open(filepath, 'r') as f:
            lines = f.readlines()
        
        # Find package line
        pkg_idx = -1
        for i, line in enumerate(lines):
            if line.startswith('package '):
                pkg_idx = i
                break
        
        if pkg_idx > 0:
            pkg_line = lines.pop(pkg_idx)
            lines.insert(0, pkg_line)
            
        with open(filepath, 'w') as f:
            f.writelines(lines)

