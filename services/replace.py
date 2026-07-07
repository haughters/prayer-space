import os
import glob

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    new_content = content.replace(
        "import org.springframework.boot.test.mock.mockito.MockBean;",
        "import org.springframework.test.context.bean.override.mockito.MockitoBean;"
    )
    new_content = new_content.replace("@MockBean", "@MockitoBean")
    new_content = new_content.replace(
        "import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;",
        "import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;"
    )
    new_content = new_content.replace(
        "import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;",
        "import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;"
    )

    if content != new_content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Updated {filepath}")

for root, _, files in os.walk('/Users/jamie/Projects/prayer-link/services'):
    for file in files:
        if file.endswith('.java'):
            process_file(os.path.join(root, file))
