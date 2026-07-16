import os

directories = [
    'services/admin-service/src/main/java/com/prayerlink/admin/repository',
    'services/group-service/src/main/java/com/prayerlink/group/repository',
    'services/identity-service/src/main/java/com/prayerlink/identity/repository',
    'services/prayer-service/src/main/java/com/prayerlink/prayer/repository'
]

replacements = {
    'Prayerupdate.SCHEMA': 'PrayerUpdate.SCHEMA',
    'Groupmember.SCHEMA': 'GroupMember.SCHEMA',
    'Intercessoraccount.SCHEMA': 'IntercessorAccount.SCHEMA'
}

for d in directories:
    if not os.path.exists(d): continue
    for filename in os.listdir(d):
        if not filename.endswith('.java'): continue
        filepath = os.path.join(d, filename)
        with open(filepath, 'r') as f:
            content = f.read()
        
        for old, new in replacements.items():
            content = content.replace(old, new)
            
        with open(filepath, 'w') as f:
            f.write(content)

