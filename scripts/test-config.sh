#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk}"
export PATH="$JAVA_HOME/bin:$PATH"
deps="${TMPDIR:-/tmp}/saman-config-tests"
mkdir -p "$deps"
for item in org/json/json/20240303/json-20240303 junit/junit/4.13.2/junit-4.13.2 org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3; do
  file="$deps/${item##*/}.jar"
  test -s "$file" || curl -fsSL "https://repo.maven.apache.org/maven2/$item.jar" -o "$file"
done
cp="$deps/json-20240303.jar:$deps/junit-4.13.2.jar:$deps/hamcrest-core-1.3.jar"
main=android-app/app/src/main/java/com/saman/tunnel
tests=android-app/app/src/test/java/com/saman/tunnel
sources=()
classes=()
for name in ConfigParser XrayConfig SubscriptionInfo; do
  test ! -f "$main/$name.kt" || sources+=("$main/$name.kt")
  if test -f "$tests/${name}Test.kt"; then
    sources+=("$tests/${name}Test.kt")
    classes+=("com.saman.tunnel.${name}Test")
  fi
done
kotlinc -J-Dkotlin.colors.enabled=false "${sources[@]}" -cp "$cp" -include-runtime -d "$deps/tests.jar"
java -cp "$deps/tests.jar:$cp" org.junit.runner.JUnitCore "${classes[@]}"
