#!/usr/bin/env python3
"""Integration checks against real JVM execution; Python stdlib only."""
import concurrent.futures
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)

def validate(result):
    assert result['expected'], result
    assert result['parents'] == result['workers'] == 4
    if result['mode'] == 'starved':
        assert result['completed'] == result['childrenStarted'] == 0
        assert result['queued'] == result['active'] == 4
        assert 'FutureTask.get' in result['dump']
        assert 'WAITING' in result['dump']
    else:
        assert result['completed'] == result['childrenStarted'] == 4
        assert result['queued'] == 0
    report = Path(result['report'])
    assert report.is_file()
    report_html = report.read_text()
    assert 'Expected behavior observed' in report_html and '예상한 동작을 확인했습니다' in report_html
    assert 'data-language="ko"' in report_html and 'data-language="en"' in report_html
    assert 'labI18n' in report_html and '<script src=' not in report_html  # offline report embeds its language controller
    assert Path(result['recording']).stat().st_size > 1000
    assert json.loads((report.parent / 'result.json').read_text()) == result
    assert (report.parent / 'threads.txt').read_text() == result['dump']

for iteration in range(3):
    completed = subprocess.run(['./jvmlab', 'run', 'compare'], capture_output=True, text=True, timeout=40, check=True)
    results = [json.loads(line) for line in completed.stdout.splitlines()]
    assert [r['mode'] for r in results] == ['starved', 'fixed']
    for result in results:
        validate(result)
    print(f'PASS real JVM comparison {iteration + 1}/3 (process exits after worker cleanup)')

jfr = str(Path(os.environ['JAVA_HOME']) / 'bin/jfr') if os.environ.get('JAVA_HOME') else shutil.which('jfr')
if jfr:
    summary = subprocess.run([jfr, 'summary', results[0]['recording']], capture_output=True, text=True, timeout=15, check=True)
    assert 'jdk.ThreadPark' in summary.stdout
    events = subprocess.run([jfr, 'print', '--events', 'jdk.ThreadPark', results[0]['recording']], capture_output=True, text=True, timeout=15, check=True)
    assert 'FutureTask' in events.stdout and 'lab-' in events.stdout
    print('PASS JFR parses and includes actual worker FutureTask park evidence')
else:
    raise AssertionError('A JDK jfr executable is required for artifact verification')

invalid = subprocess.run(['./jvmlab', 'run', 'unknown'], capture_output=True, text=True, timeout=20)
assert invalid.returncode != 0
print('PASS invalid scenario rejected')

with socket.socket() as sock:
    sock.bind(('127.0.0.1', 0))
    port = sock.getsockname()[1]
base = f'http://127.0.0.1:{port}'
server = subprocess.Popen(['./jvmlab', 'serve', str(port)], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True, start_new_session=True)

def request(path, method='GET', origin=None):
    headers = {'Origin': origin} if origin else {}
    req = urllib.request.Request(base + path, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()

try:
    deadline = time.monotonic() + 20
    while True:
        try:
            assert request('/api/health')[0] == 200
            break
        except (OSError, urllib.error.URLError):
            if server.poll() is not None or time.monotonic() > deadline:
                raise AssertionError('Server did not start')
            time.sleep(.1)
    page = request('/')[1]
    assert 'Four workers.' in page and '워커는 네 개.' in page
    assert 'data-language="ko"' in page and 'data-language="en"' in page
    assert request('/language.js')[0] == 200
    assert 'lab-language-change' in request('/language.js')[1]
    assert request('/api/run?mode=starved')[0] == 405
    assert request('/api/run?mode=bad', 'POST')[0] == 400
    assert request('/api/run?mode=fixed', 'POST', 'https://example.com')[0] == 403
    assert request('/missing')[0] == 404
    print('PASS dashboard, health, invalid mode, method and origin checks')
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        tasks = [pool.submit(request, '/api/run?mode=starved', 'POST') for _ in range(2)]
        responses = [task.result() for task in tasks]
    assert sorted(code for code, _ in responses) == [200, 409], responses
    validate(json.loads(next(body for code, body in responses if code == 200)))
    code, body = request('/api/run?mode=fixed', 'POST', base)
    assert code == 200
    validate(json.loads(body))
    print('PASS concurrent run rejected; subsequent fixed run succeeds')
finally:
    import signal
    os.killpg(server.pid, signal.SIGTERM)
    try:
        server.wait(timeout=10)
    except subprocess.TimeoutExpired:
        os.killpg(server.pid, signal.SIGKILL)
        server.wait(timeout=5)
print('All integration checks passed.')
