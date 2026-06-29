./gradlew deployPluginJar

DEPS=$(./gradlew -q printRuntimeClasspath)

RUN_DIR="build/debug-run"
KEYSTORE_DIR="$RUN_DIR/keys/web"
KEYSTORE="$KEYSTORE_DIR/keystore.jks"

mkdir -p "$KEYSTORE_DIR"
mkdir -p ".vscode"

# Generate keystore if not exists
if [ ! -f "$KEYSTORE" ]; then
    echo "Generating keystore..."

    keytool -genkeypair \
        -alias testserver \
        -keyalg RSA \
        -keysize 2048 \
        -storetype JKS \
        -keystore "$KEYSTORE" \
        -validity 3650 \
        -storepass changeit \
        -keypass changeit \
        -dname "CN=localhost, OU=Development, O=IGS, L=Local, ST=Local, C=DE"
fi

cat > .vscode/tasks.json <<EOF
{
    "version": "2.0.0",
    "tasks": [
        {
            "label": "deployPluginJar",
            "type": "shell",
            "command": "./gradlew deployPluginJar",
            "group": "build",
            "problemMatcher": []
        }
    ]
}
EOF

cat > .vscode/launch.json <<EOF
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "request": "launch",
            "name": "Run test server",
            "mainClass": "de.igslandstuhl.database.Application",
            "cwd": "\${workspaceFolder}/$RUN_DIR",
            "preLaunchTask": "deployPluginJar",
            "classPaths": ["$DEPS"]
        }
    ]
}
EOF