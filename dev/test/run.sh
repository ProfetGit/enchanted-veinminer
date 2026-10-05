#!/usr/bin/env bash
# Usage: dev/test/run.sh <mc-version> [workdir] [harness args...]
# Boots a headless server from the local ModrinthApp jar with this add-on and Veinminer (built from ../../packs/Veinminer),
# then runs EnchantedVeinminerTest. Test runs also add Veinminer 1.0.0 (from git, disabled by the tests).
# KEEP_WORLD=1 reuses the workdir's world and datapacks as they are (restart tests).
# PLATFORM=paper|purpur|spigot|bukkit runs the same scenarios on that plugin platform with the plugin jar
# (../../tools/PluginJar/platform.py sets up the work dir; default work dir .work/<ver>-<platform>).
set -euo pipefail

VER=${1:?usage: run.sh <mc-version> [workdir] [args...]}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
BASE=${VEINMINER_DIR:-$ROOT/../../packs/Veinminer}
OLD_REF=${VEINMINER_OLD_REF:-41d19a3}
PLATFORM=${PLATFORM:-vanilla}
WORK=${2:-$ROOT/dev/test/.work/$VER$([ "$PLATFORM" = vanilla ] || echo "-$PLATFORM")}
shift $(( $# >= 2 ? 2 : 1 ))
META=${MODRINTH_META:-$HOME/.local/share/ModrinthApp/meta}

VDIR=$(ls -d "$META"/versions/"$VER"-* 2>/dev/null | sort -V | tail -1)
[ -n "$VDIR" ] || { echo "no jar for $VER under $META/versions" >&2; exit 2; }
JAR="$VDIR/$(basename "$VDIR").jar"
JAVA=java
case "$VER" in
  1.*)
    # 1.x jars are obfuscated: the harness runs on the Mojang-named jar Loom made for the mod targets
    JAR=$(ls "$HOME"/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/"$VER"-loom*/*.jar 2>/dev/null | head -1)
    [ -n "$JAR" ] || { echo "no Mojang-named jar for $VER: build a mod target for it first (e.g. ../../mods/FarOut ./gradlew :$VER-fabric:build)" >&2; exit 2; }
    JREL=21
    JAVA=$(ls -d "$META"/java_versions/zulu21*/bin | head -1)/java ;;
esac

CP=$(python3 - "$VDIR/$(basename "$VDIR").json" "$META/libraries" <<'EOF'
import json, os, sys
spec, libroot = sys.argv[1], sys.argv[2]
out = []
for lib in json.load(open(spec))["libraries"]:
    parts = lib["name"].split(":")
    if len(parts) != 3 or parts[0] in ("net.fabricmc", "org.ow2.asm") or parts[1].startswith("lwjgl"):
        continue
    g, a, v = parts
    p = os.path.join(libroot, *g.split("."), a, v, f"{a}-{v}.jar")
    if os.path.exists(p):
        out.append(p)
print(":".join(out))
EOF
)

[ -n "${SKIP_BUILD:-}" ] || python3 "$ROOT/dev/build.py" >/dev/null
[ -n "${SKIP_BUILD:-}" ] || python3 "$BASE/dev/build.py" >/dev/null
case "$VER" in 1.*) ZIP=$(ls "$ROOT"/dist/EnchantedVeinminer-*-mc"$VER".zip | head -1) ;; *) ZIP=$(ls "$ROOT"/dist/EnchantedVeinminer-*.zip | grep -v -- "-mc" | head -1) ;; esac
case "$VER" in 1.*) BASEZIP=$(ls "$BASE"/dist/Veinminer-*-mc"$VER".zip | head -1) ;; *) BASEZIP=$(ls "$BASE"/dist/Veinminer-*.zip | grep -v -- "-mc" | head -1) ;; esac

if [ -z "${KEEP_WORLD:-}" ]; then
  rm -rf "$WORK"
  mkdir -p "$WORK/world/datapacks"
  [ "$PLATFORM" != vanilla ] || cp "$ZIP" "$BASEZIP" "$WORK/world/datapacks/"
  if [ "${1:-}" != explore ]; then
    mkdir -p "$WORK/world/datapacks/veinminer-1.0.0"
    case "$VER" in
      1.*) OLDTMP=$(mktemp -d); git -C "$BASE" archive "$OLD_REF" pack | tar -x -C "$OLDTMP" --strip-components=1
           python3 "$ROOT/../../tools/Backport/legacy.py" tree "$OLDTMP" "$BASE/legacy" "$VER" "$WORK/world/datapacks/veinminer-1.0.0"; rm -rf "$OLDTMP" ;;
      *) git -C "$BASE" archive "$OLD_REF" pack | tar -x -C "$WORK/world/datapacks/veinminer-1.0.0" --strip-components=1 ;;
    esac
  fi
  for extra in ${EXTRA_PACKS:-}; do cp -r "$extra" "$WORK/world/datapacks/"; done
  echo "eula=true" > "$WORK/eula.txt"
  PORT=${PORT:-$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1])')}
cat > "$WORK/server.properties" <<EOF
level-type=minecraft\\:flat
generate-structures=false
online-mode=false
server-ip=127.0.0.1
server-port=$PORT
spawn-protection=0
view-distance=3
simulation-distance=3
difficulty=peaceful
sync-chunk-writes=false
enable-query=false
enable-rcon=false
EOF
fi

rm -rf "$WORK/classes"
mkdir -p "$WORK/classes"
RUN_CP="$JAR:$CP"
JOPTS=()
if [ "$PLATFORM" != vanilla ]; then
  { read -r RUN_CP; read -r PMAIN; read -r PACKS; } < <(python3 "$ROOT/../../tools/PluginJar/platform.py" "$PLATFORM" "$VER" "$WORK" "$BASE" "$ROOT")
  JOPTS=("-Dharness.main=$PMAIN" "-Dharness.packs=$PACKS")
fi
SRC="$ROOT/dev/test/EnchantedVeinminerTest.java"
SEDS=()
case "$VER" in
  1.21.8|1.21.4|1.21.1) SEDS+=(-e 's/\bIdentifier\b/ResourceLocation/g') ;;  # ResourceLocation was renamed Identifier in 1.21.11
esac
case "$VER" in 1.21.4|1.21.1) SEDS+=(-e 's/\.enchantment()/.enchantment/g') ;; esac  # a record accessor since 1.21.5
case "$VER" in
  1.21.1) SEDS+=(-e 's/lookupOrThrow(/registryOrThrow(/g' -e 's/\.get(ResourceLocation\.parse(\([a-zA-Z]*\)))\.orElseThrow()/.getHolder(ResourceLocation.parse(\1)).orElseThrow()/g' -e 's/BuiltInRegistries\.ITEM\.getValue(/BuiltInRegistries.ITEM.get(/g') ;;
esac
if [ ${#SEDS[@]} -gt 0 ]; then mkdir -p "$WORK/src"; sed "${SEDS[@]}" "$SRC" > "$WORK/src/EnchantedVeinminerTest.java"; SRC="$WORK/src/EnchantedVeinminerTest.java"; fi
javac -nowarn ${JREL:+--release $JREL} -cp "$JAR:$CP" -d "$WORK/classes" "$SRC"
cd "$WORK"
# one machine-wide server slot (ModTest/slots.py), shared with every other session and harness
if [ -f "$ROOT/../../tools/ModTest/slot.sh" ]; then . "$ROOT/../../tools/ModTest/slot.sh"; slot_acquire server "EnchantedVeinminer $VER${PLATFORM:+ $PLATFORM}"; fi
set +e
"$JAVA" -Xmx2G --add-opens java.base/java.lang=ALL-UNNAMED "${JOPTS[@]}" -cp "$WORK/classes:$RUN_CP" EnchantedVeinminerTest "$@" 2>&1 | tee "$WORK/harness.log" | grep -oE '\[(PASS|FAIL|INFO|EXPLORE)\].*|SUMMARY.*|  - .*|[A-Za-z.]*Exception.*'
STATUS=${PIPESTATUS[0]}
set -e

echo "--- server log warnings/errors (test-env noise filtered) ---"
grep -E '/(WARN|ERROR)\]' "$WORK/logs/latest.log" | grep -v 'STDOUT' \
  | grep -vE 'OFFLINE/INSECURE|authenticate usernames|online-mode|hackers to connect|No key layers in MapLike' || echo "none"
exit $STATUS
