#!/usr/bin/env python3
"""Run two real Fabric clients against a fresh, local Lophine 26.2 server."""
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import uuid
import zipfile
import yaml

ROOT = Path(__file__).resolve().parents[2]
SEED = Path(os.environ.get("FM_SERVER_SEED", ROOT.parent / "thw_servers/26.2/test-server"))
PLUGIN_ROOT = ROOT.parent / "FeatherMorph"
FORMS = ["cow", "pig", "chicken", "cat", "wolf", "sheep", "mooshroom", "nautilus",
         "evoker", "slime", "magma_cube", "sulfur_cube", "mannequin", "copper_golem", "horse", "ender_dragon", "player:Notch"]
AUDIT_RUN = Path(sys.argv[2]).resolve() if len(sys.argv) == 3 and sys.argv[1] == "--audit-run" else None
SCREENS_ONLY = os.environ.get("FM_SCREEN_VALIDATION_ONLY") == "true"
PROTOCOL_ONLY = os.environ.get("FM_PROTOCOL_VALIDATION_ONLY") == "true"
ANIMATION_ENTITY = os.environ.get("FM_ANIMATION_ENTITY", "")
assert not (SCREENS_ONLY and PROTOCOL_ONLY), "Select one focused validation mode"
assert not ANIMATION_ENTITY or PROTOCOL_ONLY, "Entity filtering requires FM_PROTOCOL_VALIDATION_ONLY=true"
API_VERSION = int(os.environ.get("FM_TEST_API", "19"))
assert API_VERSION in [17, 19], "FM_TEST_API must be 17 or 19"
assert API_VERSION == 19 or SCREENS_ONLY, "API 17 compatibility checks require FM_SCREEN_VALIDATION_ONLY=true"
RUN = AUDIT_RUN or Path(tempfile.mkdtemp(prefix="feathermorphclient-26.2-"))
REPORT = {"status": "RUNNING", "directory": str(RUN), "checks": [],
          "commandApi": API_VERSION,
          "scope": "screens" if SCREENS_ONLY else "protocol" if PROTOCOL_ONLY else "full",
          "animationEntity": ANIMATION_ENTITY or "all",
          "notRun": ["all skills and abilities", "cross-region locator connections", "NeoForge"]}
CLIENTS = []
SERVER = None
HANDLES = []
print("Evidence:", RUN, flush=True)


def log(path):
    handle = path.open("w")
    HANDLES.append(handle)
    return handle


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def pass_check(name):
    REPORT["checks"].append(name)
    print("PASS", name, flush=True)


def until(name, condition, timeout=60):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        value = condition()
        if value:
            return value
        if SERVER is not None and SERVER.poll() is not None:
            raise RuntimeError("Server exited early: " + name)
        time.sleep(0.1)
    raise TimeoutError(name)


def server_command(command):
    SERVER.stdin.write(command + "\n")
    SERVER.stdin.flush()


def grant_disguise(name, identifier):
    server_command(f"fm manage grant {name} {identifier}")
    # Folia schedules console grants on the player's region independently of client commands.
    until(f"grant {identifier} to {name}",
          lambda: f"Granted disguise '{identifier}' to {name}" in (RUN / "server.log").read_text())


def events(client):
    path = client["dir"] / "events.jsonl"
    records = [json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []
    failed = [r for r in records if r["status"] == "FAIL"]
    if failed:
        raise AssertionError(f"{client['name']}: {failed}")
    if client["process"].poll() is not None:
        raise RuntimeError(f"{client['name']} exited: {client['process'].returncode}")
    return records


def action(client, kind, value="", delay_ms=2500):
    token = f"{len(REPORT['checks'])}_{kind}"
    path = client["dir"] / "action.json"
    staging = path.with_suffix(".tmp")
    staging.write_text(json.dumps({"id": token, "type": kind, "value": value, "delayMs": delay_ms}))
    staging.replace(path)
    until(f"{client['name']}: {kind} {value}", lambda: any(r["id"] == token for r in events(client)))
    pass_check(f"{client['name']}: {kind} {value}")


def build_server_probe():
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
    api = next((cache / "io.papermc.paper/paper-api/26.2.build.124-stable").rglob("*.jar"))
    classpath = [api]
    for group in ["net.kyori", "org.jetbrains/annotations", "com.google.guava/guava", "org.jspecify/jspecify"]:
        classpath.extend((cache / group).rglob("*.jar"))
    classes = RUN / "server-probe-classes"
    classes.mkdir()
    subprocess.run(["javac", "--release", "25", "-cp", os.pathsep.join(map(str, classpath)),
                    "-d", str(classes), str(ROOT / "validation/26.2/server/BackendProbe.java")],
                   check=True, stdout=log(RUN / "server-probe-build.log"), stderr=subprocess.STDOUT)
    target = RUN / "BackendProbe.jar"
    with zipfile.ZipFile(target, "w") as jar:
        jar.write(classes / "BackendProbe.class", "BackendProbe.class")
        jar.write(ROOT / "validation/26.2/server/plugin.yml", "plugin.yml")
    return target


def check_logs():
    allowed = ["Failed to fetch user properties", "Error while loading the narrator",
               "Failed to fetch Realms feature flags", "Failed to retrieve profile key pair"]
    environment, unexpected = {}, {}
    paths = [RUN / "server.log"] + [c["dir"] / "client.log" for c in CLIENTS]
    for path in paths:
        contents = path.read_text()
        for line in contents.splitlines():
            if re.search(r"\bERROR\b|\[STDERR\]: .*?(?:Exception|Error)|未处理的异常|FM_CLIENT_BACKEND_FAIL", line):
                missing_audio = "Error starting SoundSystem. Turning off sounds & music" in line and "Failed to open OpenAL device" in contents
                destination = environment if path.name == "client.log" and (any(s in line for s in allowed) or missing_audio) else unexpected
                destination.setdefault(str(path.relative_to(RUN)), []).append(line)
    REPORT["environmentErrors"] = environment
    REPORT["logIssues"] = unexpected
    assert not unexpected, f"Unexpected runtime errors: {unexpected}"
    observer_log = (RUN / "MorphObserver/client.log").read_text()
    assert "OK Added dynamic entity whitelist rule" in observer_log
    assert "entity_model_features 3.3.11" in observer_log and "entity_texture_features 7.2.5" in observer_log
    pass_check("optional compatibility mods active and no unexpected runtime errors")


if AUDIT_RUN:
    # Recheck retained logs without repeating successful gameplay, or masking a gameplay failure.
    result_file = RUN / "result.json"
    previous = result_file.read_text()
    REPORT = json.loads(previous)
    assert all(name in REPORT["checks"] for name in ["both clients shut down normally", "server shut down normally"])
    CLIENTS = [{"dir": RUN / name} for name in ["MorphActor", "MorphObserver"]]
    for client in CLIENTS:
        records = [json.loads(line) for line in (client["dir"] / "events.jsonl").read_text().splitlines()]
        assert records and all(record["status"] == "PASS" for record in records)
        assert any(record["id"] == "stop" for record in records)
    props = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text().splitlines()
                 if "=" in line and not line.startswith("#"))
    artifact = ROOT / "fabric/build/libs" / f"feathermorph_client-fabric-26.2-{props['version']}.jar"
    assert digest(artifact) == REPORT["artifacts"][artifact.name], "Production artifact changed since this run"
    check_logs()
    backup = RUN / "result-before-log-audit.json"
    if not backup.exists():
        backup.write_text(previous)
    REPORT["status"] = "PASS"
    REPORT.pop("error", None)
    REPORT["logAudit"] = "Rechecked retained logs; missing OpenAL device is recorded as an environment error."
    result_file.write_text(json.dumps(REPORT, indent=2))
    print(f"PASS retained run: {len(REPORT['checks'])} checks", flush=True)
    sys.exit(0)


def start_client(name, runtime, mods, optional=False):
    directory = RUN / name
    (directory / "mods").mkdir(parents=True)
    (directory / "config").mkdir()
    (directory / "config/morphclient.json").write_text(json.dumps({
        "alwaysShowPreviewInInventory": True, "protocolCompatibilityMode": API_VERSION == 17,
        "verbosePackets": True}))
    (directory / "options.txt").write_text(
        'version:4903\nenableVsync:false\nmaxFps:30\nrenderDistance:2\nsimulationDistance:5\n'
        'mipmapLevels:0\nentityShadows:false\ngraphicsPreset:"fast"\npreferredGraphicsBackend:"opengl"\n'
        'pauseOnLostFocus:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\nnarrator:0\n')
    for source in mods + (optional_mods if optional else []):
        shutil.copy2(source, directory / "mods" / source.name)
    command = [os.environ.get("FM_JAVA", "java"), "-Xmx1G", "-XX:ActiveProcessorCount=2",
               "--enable-native-access=ALL-UNNAMED", "-Dfabric.defaultModDistributionNamespace=official",
               f"-Dfm.probe.dir={directory}", f"-Dfm.probe.address=127.0.0.1:{port}",
               f"-Dfm.probe.api={API_VERSION}",
               "-cp", os.pathsep.join(runtime), "net.fabricmc.loader.impl.launch.knot.KnotClient",
               "--username", name, "--uuid", str(uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + name).encode()).digest(), version=3)),
               "--accessToken", "0", "--version", "26.2", "--versionType", "release",
               "--gameDir", str(directory), "--assetsDir", runtime_info["assetsDir"],
               "--assetIndex", "26.2-32", "--width", "640", "--height", "360"]
    environment = os.environ.copy()
    environment["LIBGL_ALWAYS_SOFTWARE"] = "1"
    process = subprocess.Popen(command, cwd=directory, env=environment,
                               stdout=log(directory / "client.log"), stderr=subprocess.STDOUT)
    client = {"name": name, "dir": directory, "process": process}
    CLIENTS.append(client)
    until(name + " handshake", lambda: any(r["id"] == "handshake" for r in events(client)), 120)
    pass_check(f"{name}: registry, startup, API {API_VERSION} handshake and property precedence/cache")
    return client


try:
    if not os.environ.get("DISPLAY"):
        raise RuntimeError("Set DISPLAY to a desktop or Xvfb display")
    with log(RUN / "build.log") as output:
        subprocess.run([str(ROOT / "gradlew"), "build", ":fabric:prepareUpgradeClientRuntime", "--no-daemon", "--console=plain"],
                       cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, check=True, timeout=300)
    props = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text().splitlines()
                 if "=" in line and not line.startswith("#"))
    artifact = ROOT / "fabric/build/libs" / f"feathermorph_client-fabric-26.2-{props['version']}.jar"
    with zipfile.ZipFile(artifact) as jar:
        descriptor = json.loads(jar.read("fabric.mod.json"))
        assert descriptor["depends"]["minecraft"] == "26.2"
        assert descriptor["depends"]["java"] == ">=25"
        assert not any("validation/" in name or "UpgradeClientProbe" in name for name in jar.namelist())
        assert json.loads(jar.read("pack.mcmeta"))["pack"]["min_format"] == [88, 0]
        assert json.loads(jar.read("pack.mcmeta"))["pack"]["max_format"] == [88, 0]
        assert len(descriptor["jars"]) == 2
    runtime_info = json.loads((ROOT / "fabric/build/validation/runtime.json").read_text())
    runtime, mods = [], [artifact, ROOT / "fabric/build/validation/FeatherMorphClientUpgradeProbe.jar"]
    for value in runtime_info["classpath"]:
        path = Path(value)
        if "com.github.NiFeather" in value:
            continue  # These libraries must come from the production artifact's nested JARs.
        with zipfile.ZipFile(path) as jar:
            if "fabric.mod.json" in jar.namelist() and "fabric-loader" not in path.name:
                mods.append(path)
            else:
                runtime.append(value)
    assert any("minecraft" in path for path in runtime)
    optional_mods = []
    for mod, version in [("entity-model-features", props["emf_version"]),
                         ("entitytexturefeatures", props["etf_version"]), ("entityculling", props["entityculling_version"])]:
        base = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/maven.modrinth" / mod / version
        optional_mods.append(next(base.rglob("*.jar")))
    plugin_version = dict(line.split("=", 1) for line in (PLUGIN_ROOT / "gradle.properties").read_text().splitlines()
                          if "=" in line and not line.startswith("#"))["project_version"]
    plugin = PLUGIN_ROOT / "build/libs" / f"feathermorph-{plugin_version}-26.2-final.jar"
    packet_events = SEED / "plugins/packetevents-spigot-2.14.0.jar"
    REPORT["artifacts"] = {p.name: digest(p) for p in [artifact, plugin, SEED / "server.jar", packet_events]}
    pass_check("production artifact and separately packaged probe")
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", int(os.environ.get("FM_TEST_PORT", "0"))))
        port = listener.getsockname()[1]
    server_dir = RUN / "server"
    server_dir.mkdir()
    for name in ["server.jar", "cache", "libraries", "versions"]:
        subprocess.run(["cp", "-a", "--reflink=auto", str(SEED / name), str(server_dir / name)], check=True)
    (server_dir / "plugins/FeatherMorph").mkdir(parents=True)
    shutil.copytree(SEED / "plugins/FeatherMorph/mclang", server_dir / "plugins/FeatherMorph/mclang")
    shutil.copy2(plugin, server_dir / "plugins/FeatherMorph.jar")
    shutil.copy2(packet_events, server_dir / "plugins/PacketEvents.jar")
    shutil.copy2(build_server_probe(), server_dir / "plugins/BackendProbe.jar")
    with zipfile.ZipFile(plugin) as jar:
        config = yaml.safe_load(jar.read("config.yml"))
    config["root"]["check_update"] = False
    config["root"]["language"]["code"] = "en_us"
    config["root"]["language"]["single_language"] = True
    config["root"]["client"]["log_incoming_packets"] = True
    config["root"]["client"]["log_outgoing_packets"] = True
    (server_dir / "plugins/FeatherMorph/config.yml").write_text(yaml.safe_dump(config, allow_unicode=True))
    (server_dir / "eula.txt").write_text("eula=true\n")
    flat = {"biome": "minecraft:plains", "layers": [{"block": "minecraft:bedrock", "height": 1},
            {"block": "minecraft:dirt", "height": 2}, {"block": "minecraft:grass_block", "height": 1}],
            "lakes": False, "features": False, "structure_overrides": []}
    (server_dir / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\n"
        "max-players=3\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\n"
        f"level-type=minecraft:flat\ngenerator-settings={json.dumps(flat)}\nlevel-seed=2602\n"
        "difficulty=normal\ngamemode=creative\nallow-flight=true\nenable-rcon=false\nenable-query=false\n")
    SERVER = subprocess.Popen([os.environ.get("FM_JAVA", "java"), "-Xms512M", "-Xmx1G", "-XX:ActiveProcessorCount=2",
                               "-Duser.language=en", "-Duser.country=US",
                               "-jar", "server.jar", "--nogui"], cwd=server_dir, stdin=subprocess.PIPE,
                              stdout=log(RUN / "server.log"), stderr=subprocess.STDOUT, text=True)
    until("server startup", lambda: "Done (" in (RUN / "server.log").read_text(), 180)
    server_command("gamerule minecraft:spawn_mobs false")
    actor = start_client("MorphActor", runtime, mods)
    observer = start_client("MorphObserver", runtime, mods, optional=True)
    for name in ["MorphActor", "MorphObserver"]:
        server_command("op " + name)
        server_command(f"tp {name} {0.5 if name == 'MorphActor' else 3.5} -60 0.5")
        for form in FORMS:
            identifier = form if ":" in form else "minecraft:" + form
            grant_disguise(name, identifier)
    time.sleep(2)
    action(actor, "self", "true")
    for form in ([] if SCREENS_ONLY or PROTOCOL_ONLY else FORMS):
        identifier = form if ":" in form else "minecraft:" + form
        action(actor, "morph", identifier)
        action(observer, "observe_native", identifier)
        if form in ["cow", "slime", "sulfur_cube"]:
            action(actor, "screenshot", form)
            action(observer, "screenshot", form)
        action(actor, "unmorph")
    if API_VERSION == 17:
        action(actor, "morph", "minecraft:cat")
        action(actor, "legacy_animation", "sit")
        action(actor, "legacy_animation", "standup")
        action(actor, "unmorph")
        grant_disguise("MorphActor", "minecraft:shulker")
        for name in ["open", "peek"]:
            action(actor, "morph", "minecraft:shulker")
            action(actor, "legacy_animation", name, delay_ms=1500 if name == "open" else 1000)
            action(actor, "screenshot", "legacy-" + name, delay_ms=250)
            time.sleep(6)
            action(actor, "temporary", "{}", delay_ms=250)
            action(actor, "unmorph")
    if not SCREENS_ONLY:
        animations = [
            ("cat", "sit", {"cat/sitting": "true", "cat/lying": "false"}, "standup"),
            ("cat", "lay", {"cat/sitting": "false", "cat/lying": "true"}, "standup"),
            ("wolf", "sit", {"wolf/sitting": "true"}, "standup"),
            ("fox", "sleep", {"fox/status": "SLEEPING"}, "standup"),
            ("fox", "sit", {"fox/status": "SITTING"}, "standup"),
            ("panda", "sit", {"panda/sitting": "true"}, "standup"),
            ("allay", "dance", {"allay/dancing": "true"}, "stop"),
            ("piglin", "dance", {"piglin/dancing": "true"}, "stop"),
            ("pufferfish", "inflate", {"pufferfish/puff_state": "LARGE"}, "deflate"),
            ("creaking", "eye_glow", {"creaking/eyes_glowing": "true"}, "stop_eye_glow"),
            ("mannequin", "crawl", {"entity/static_pose": "SWIMMING"}, "standup"),
            ("mannequin", "lay", {"entity/static_pose": "SLEEPING", "living_entity/bed_pos": "[0,-60,0]"}, "standup"),
            ("player:Notch", "lay", {"entity/static_pose": "SLEEPING", "living_entity/bed_pos": "[0,-60,0]"}, "standup"),
            ("player:Notch", "crawl", {"entity/static_pose": "SWIMMING"}, "standup"),
        ]
        for form, name, expected, reset in animations:
            if ANIMATION_ENTITY and form != ANIMATION_ENTITY:
                continue
            identifier = form if ":" in form else "minecraft:" + form
            grant_disguise("MorphActor", identifier)
            action(actor, "morph", identifier)
            action(actor, "animation", json.dumps({"name": name, "properties": expected}))
            action(actor, "rebuild", json.dumps(expected))
            server_command("fmprotocolprobe MorphActor refresh")
            action(actor, "temporary", json.dumps(expected))
            action(actor, "animation", json.dumps({"name": reset, "properties": {}}))
            action(actor, "unmorph")
        for form, name, expected, duration in [
                ("armadillo", "rollup", {"armadillo/state": "SCARED"}, 1000),
                ("shulker", "open", {"shulker/shell_height": "100"}, 1500),
                ("shulker", "peek", {"shulker/shell_height": "30"}, 1000),
                ("sniffer", "sniff", {"sniffer/state": "SNIFFING"}, 250),
                ("frog", "eat", {"entity/static_pose": "USING_TONGUE"}, 250),
                ("warden", "sniff", {"entity/static_pose": "SNIFFING"}, 1000),
                ("warden", "roar", {"entity/static_pose": "ROARING"}, 1000)]:
            if ANIMATION_ENTITY and form != ANIMATION_ENTITY:
                continue
            identifier = "minecraft:" + form
            grant_disguise("MorphActor", identifier)
            action(actor, "morph", identifier)
            action(actor, "animation", json.dumps({"name": name, "properties": expected}), delay_ms=duration)
            if form == "shulker":
                action(actor, "screenshot", name, delay_ms=250)
            time.sleep(6)
            action(actor, "temporary", "{}", delay_ms=250)
            action(actor, "unmorph")
        if not ANIMATION_ENTITY or ANIMATION_ENTITY == "warden":
            action(actor, "morph", "minecraft:warden")
            action(actor, "animation", json.dumps({"name": "digdown", "properties": {"entity/static_pose": "DIGGING"}}), delay_ms=1000)
            time.sleep(6)
            vanished = {"living_entity/invisible": "true"}
            action(actor, "temporary", json.dumps(vanished), delay_ms=250)
            action(actor, "rebuild", json.dumps(vanished))
            server_command("fmprotocolprobe MorphActor refresh")
            action(actor, "temporary", json.dumps(vanished))
            for name in ["sniff", "roar"]:
                action(actor, "animation", json.dumps({"name": name, "properties": vanished}), delay_ms=1000)
                time.sleep(6)
                action(actor, "temporary", json.dumps(vanished), delay_ms=250)
            action(actor, "animation", json.dumps({"name": "appear", "properties": {"entity/static_pose": "EMERGING"}}), delay_ms=1000)
            time.sleep(6)
            action(actor, "temporary", "{}", delay_ms=250)
            action(actor, "unmorph")
        action(actor, "morph", "minecraft:horse")
        server_command("fmprotocolprobe MorphActor equipment")
        action(actor, "equipment")
        server_command("fmprotocolprobe MorphActor property")
        action(actor, "persistent", "true")
        server_command("fmprotocolprobe MorphActor discard")
        action(actor, "persistent", "false")
        action(actor, "unmorph")
    if not PROTOCOL_ONLY:
        server_command("fmclientbackend")
        until("client backend selected", lambda: "FM_CLIENT_BACKEND_PASS" in (RUN / "server.log").read_text())
        pass_check("client renderer backend selected in temporary server")
    for form in ([] if SCREENS_ONLY or PROTOCOL_ONLY else ["cow", "slime", "magma_cube", "sulfur_cube", "player:Notch"]):
        identifier = form if ":" in form else "minecraft:" + form
        action(actor, "morph", identifier)
        action(observer, "observe", identifier)
        action(actor, "unmorph")
    action(actor, "morph", "minecraft:cow")
    for screen in ([] if PROTOCOL_ONLY else ["disguise", "quick", "emote", "inventory", "creative", "config", "close"]):
        if screen in ["inventory", "creative"]:
            server_command(f"gamemode {'survival' if screen == 'inventory' else 'creative'} MorphActor")
            time.sleep(0.5)
        action(actor, "screen", screen)
    action(actor, "camera", "first")
    action(actor, "screenshot", "first-person")
    action(actor, "camera", "third")
    action(actor, "self", "false")
    action(actor, "self", "true")
    action(actor, "unmorph")
    for client in CLIENTS:
        path = client["dir"] / "action.json"
        path.write_text(json.dumps({"id": "stop", "type": "stop", "value": ""}))
        client["process"].wait(timeout=30)
        assert client["process"].returncode == 0
    pass_check("both clients shut down normally")
    SERVER.stdin.write("stop\n")
    SERVER.stdin.flush()
    SERVER.wait(timeout=60)
    assert SERVER.returncode == 0
    pass_check("server shut down normally")
    check_logs()
    REPORT["status"] = "PASS"
except BaseException as error:
    REPORT["status"] = "FAIL"
    REPORT["error"] = repr(error)
    raise
finally:
    for client in CLIENTS:
        if client["process"].poll() is None:
            client["process"].terminate()
            try:
                client["process"].wait(timeout=10)
            except subprocess.TimeoutExpired:
                client["process"].kill()
                client["process"].wait()
    if SERVER is not None and SERVER.poll() is None:
        try:
            SERVER.stdin.write("stop\n")
            SERVER.stdin.flush()
            SERVER.wait(timeout=60)
        except (BrokenPipeError, subprocess.TimeoutExpired):
            SERVER.terminate()
            try:
                SERVER.wait(timeout=10)
            except subprocess.TimeoutExpired:
                SERVER.kill()
                SERVER.wait()
    for handle in HANDLES:
        handle.close()
    (RUN / "result.json").write_text(json.dumps(REPORT, indent=2))
