import re

path = "android/app/build.gradle"
with open(path) as f:
    content = f.read()

signing_block = '''
    signingConfigs {
        debug {
            storeFile file("../lifa-release.keystore")
            storePassword "LifaFamiglia2026!"
            keyAlias "lifa"
            keyPassword "LifaFamiglia2026!"
        }
    }
'''

if "signingConfigs {" not in content:
    content = content.replace("android {", "android {" + signing_block, 1)

if re.search(r'debug\s*\{[^}]*signingConfig signingConfigs\.debug', content) is None:
    content = re.sub(
        r'(buildTypes\s*\{)',
        r'\1\n        debug {\n            signingConfig signingConfigs.debug\n        }',
        content, count=1
    )

with open(path, "w") as f:
    f.write(content)
print("build.gradle aggiornato con la firma stabile.")
