"""HTTP authorization regression tests. Run after compiling Java: python tests/test_roles.py bin."""
import http.cookiejar
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

BASE = 'http://localhost:8080/api'

def request(client, path, data=None):
    req = urllib.request.Request(BASE + path, data=None if data is None else json.dumps(data).encode(),
                                 headers={'Content-Type': 'application/json'})
    try:
        response = client.open(req, timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    return response.status, json.loads(response.read())

def client():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

with tempfile.TemporaryDirectory(prefix='parking-role-test-') as work:
    with open(Path(work) / 'server.log', 'w') as log:
        server = subprocess.Popen(['java', '-cp', str(Path(sys.argv[1]).resolve()), 'server.ParkingServer'],
                                  cwd=work, stdout=log, stderr=log)
        try:
            for _ in range(60):
                if server.poll() is not None:
                    raise RuntimeError('Test server failed to start; check port 8080')
                try:
                    assert request(client(), '/status')[0] == 401
                    break
                except urllib.error.URLError:
                    time.sleep(.1)
            else:
                raise RuntimeError('Server startup timeout')
            for username, role in [('owner', 'owner'), ('admin', 'admin'), ('staff01', 'staff')]:
                c = client()
                code, account = request(c, '/login', {'username': username, 'password': role + '123'})
                assert code == 200 and account['role'] == role, (username, account)
                assert request(c, '/session')[1]['role'] == role
                code, status = request(c, '/status')
                assert code == 200 and ('totalRevenue' in status) == (role == 'owner')
                assert request(c, '/lot')[0] == 200
                assert request(c, '/dashboard/daily')[0] == (200 if role == 'owner' else 403)
                for path in ['/history', '/memberships', '/reservations', '/payments']:
                    assert request(c, path)[0] == (403 if role == 'staff' else 200), (role, path)
                if role != 'owner':
                    for path in ['/time-travel', '/ai/chat']:
                        assert request(c, path, {})[0] == 403
                if role == 'staff':
                    for path in ['/memberships', '/reservations', '/hardware/gate']:
                        assert request(c, path, {})[0] == 403
                    assert all(t['status'] != 'EXITED' for t in request(c, '/tickets')[1])
                assert request(c, '/history/extra')[0] == 404
                assert request(c, '/logout', {})[0] == 200
                assert request(c, '/status')[0] == 401
            assert request(client(), '/login', {'username':'owner','password':'wrong'})[0] == 401
            print('PASS: 3 roles, allowed/denied APIs, protected writes, totals, ticket scope, logout and bad login')
        finally:
            server.terminate()
            server.wait(timeout=10)
