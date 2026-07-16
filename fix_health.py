import os

files = [
    "services/notification-service/src/main/resources/application.yml",
    "services/group-service/src/main/resources/application.yml",
    "services/admin-service/src/main/resources/application.yml",
    "services/identity-service/src/main/resources/application.yml",
    "services/prayer-service/src/main/resources/application.yml",
]

for f in files:
    with open(f, 'r') as file:
        content = file.read()
    
    if "management:\n  health:\n    diskspace:\n      enabled: false" in content:
        continue
        
    if "management:" in content:
        content = content.replace("management:", "management:\n  health:\n    diskspace:\n      enabled: false")
    else:
        content += "\nmanagement:\n  health:\n    diskspace:\n      enabled: false\n"
        
    with open(f, 'w') as file:
        file.write(content)

print("Done!")
