"""Fetch the official ZIP into this project's private runtime; never install a service."""
from pathlib import Path
import hashlib, urllib.request, zipfile

root = Path(__file__).resolve().parents[1]
runtime = root / '.runtime'
runtime.mkdir(exist_ok=True)
version = '8.4.13'
archive = runtime / f'mysql-{version}-winx64.zip'
expected_md5 = '7b94489d5524dea9b27097068bea25a8'
url = f'https://cdn.mysql.com/Downloads/MySQL-8.4/{archive.name}'

if not archive.exists():
    partial = archive.with_suffix('.download')
    print(f'Downloading official MySQL {version} Windows ZIP...', flush=True)
    with urllib.request.urlopen(url, timeout=60) as response, partial.open('wb') as output:
        total = 0
        while chunk := response.read(1024 * 1024):
            output.write(chunk)
            total += len(chunk)
            if total % (32 * 1024 * 1024) == 0:
                print(f'{total // (1024 * 1024)} MiB received', flush=True)
    partial.replace(archive)
with archive.open('rb') as source:
    actual = hashlib.file_digest(source, 'md5').hexdigest()
if actual != expected_md5:
    raise SystemExit('Official download checksum mismatch; archive not extracted.')
print('Download checksum verified against the official download page.', flush=True)
destination = runtime / f'mysql-{version}-winx64'
if not (destination / 'bin/mysqld.exe').exists():
    with zipfile.ZipFile(archive) as package:
        for item in package.infolist():
            target = (runtime / item.filename).resolve()
            if not target.is_relative_to(runtime.resolve()):
                raise SystemExit('Unsafe ZIP entry.')
        package.extractall(runtime)
print(f'MySQL extracted: {destination}', flush=True)
