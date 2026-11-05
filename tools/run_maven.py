"""Run Maven with a project-local dependency cache and optional private test settings."""
from pathlib import Path
import json, os, shutil, subprocess, sys

root = Path(__file__).resolve().parents[1]
environment = os.environ.copy()
if 'test' in sys.argv[1:]:
    settings = json.loads((root / '.runtime/local-settings.json').read_text(encoding='utf-8-sig'))
    environment['HOTEL_DB_PASSWORD'] = settings['appPassword']
    environment['HOTEL_TEST_DB_URL'] = (
        f"jdbc:mysql://127.0.0.1:{settings['port']}/hotel_frontdesk_test?"
        'connectionTimeZone=%2B07:00&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true'
    )
maven = shutil.which('mvn.cmd') or shutil.which('mvn')
if not maven:
    raise SystemExit('Maven is required; mvn was not found on PATH.')
command = [maven]
if os.name == 'nt':
    # Invoke the same launcher as the installed mvn.cmd, directly. A nested
    # Windows cmd/console wrapper can exit before its Maven JVM has finished.
    # Waiting on java.exe itself keeps the real process/exit code authoritative.
    maven_home = Path(maven).resolve().parent.parent
    java_home = Path(environment.get('JAVA_HOME', ''))
    java = java_home / 'bin/java.exe'
    launchers = list((maven_home / 'boot').glob('plexus-classworlds-*.jar'))
    if not java.is_file() or len(launchers) != 1:
        raise SystemExit('JAVA_HOME must point to the installed JDK; Maven boot launcher must exist.')
    command = [str(java), '-classpath', str(launchers[0]),
               f'-Dclassworlds.conf={maven_home / "bin/m2.conf"}',
               f'-Dmaven.home={maven_home}',
               f'-Dmaven.multiModuleProjectDirectory={root}',
               'org.codehaus.plexus.classworlds.launcher.Launcher']
log_path = root / '.runtime' / ('maven-test.log' if 'test' in sys.argv[1:] else 'maven-build.log')
# File redirection also avoids the Windows console/pipe host cutting off a
# still-running Maven child. Keep its real exit code as the verdict.
with log_path.open('w', encoding='utf-8') as log:
    result = subprocess.run([*command, '-B', '-ntp', '-e', f'-Dmaven.repo.local={root / ".runtime/m2"}', *sys.argv[1:]], cwd=root, env=environment, stdout=log, stderr=subprocess.STDOUT)
lines = log_path.read_text(encoding='utf-8', errors='replace').splitlines()
summary = [line for line in lines if 'Tests run:' in line or 'BUILD SUCCESS' in line or 'BUILD FAILURE' in line]
for line in (summary if result.returncode == 0 else lines[-45:]):
    print(line, flush=True)
print(f'Maven log: {log_path}', flush=True)
raise SystemExit(result.returncode)
