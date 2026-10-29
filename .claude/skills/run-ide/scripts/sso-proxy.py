#!/usr/bin/env python3
#
# Copyright 2026 Urs Wolfer
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# A stand-in for a single sign-on proxy such as Azure Application Proxy in front of the Gerrit
# of gerrit.sh, for the sign-in through the IDE's browser:
#   http://localhost:8090  the proxy: forwards to Gerrit on :8080 only with a valid HttpOnly
#                          cookie, else redirects to the sign-in page
#   http://127.0.0.1:8091  the sign-in page, on another host as far as cookies go: user sso-user,
#                          password sso-pass and code 123456, then back to the proxy, which sets
#                          the cookie
# Each answers these, which the smoke test uses:
#   GET  /.sso/log           what each side received, with its Authorization and Cookie headers
#   GET  /.sso/tokens        every cookie value handed out, to look for in the IDE's files
#   POST /.sso/revoke        ends every session, as an expired cookie would
#   POST /.sso/mode/direct   the proxy shows the sign-in page itself, with a 200, as some do
#   POST /.sso/mode/redirect the proxy redirects to it again (the default)
# Usage: sso-proxy.py, which runs until it is killed
import http.cookies
import http.server
import json
import secrets
import threading
import urllib.error
import urllib.parse
import urllib.request

GERRIT, PROXY, SIGN_IN = 'http://localhost:8080', 'http://localhost:8090', 'http://127.0.0.1:8091'
COOKIE, USER, PASSWORD, CODE = 'SSOProxyAccess', 'sso-user', 'sso-pass', '123456'
sessions, tokens, codes, log = set(), [], set(), []
state = {'direct': False}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args):
        return None


forward = urllib.request.build_opener(NoRedirect, urllib.request.ProxyHandler({}))


def page(to):
    # the first field at the top left, where the smoke test clicks
    return f'''<!doctype html><html><head><meta charset="utf-8"><title>Sign in</title><style>
body {{ margin: 0; font: 16px sans-serif }} input {{ display: block; width: 320px; height: 36px; margin: 0 0 8px }}
</style></head><body><form method="post" action="{SIGN_IN}/login?to={urllib.parse.quote(to, safe='')}">
<input id="user" name="user" placeholder="User" autofocus>
<input id="password" name="password" type="password" placeholder="Password">
<input id="code" name="code" placeholder="Code">
<button id="submit">Sign in</button></form><h1>Single Sign On</h1></body></html>'''.encode()


class Handler(http.server.BaseHTTPRequestHandler):
    side = ''
    protocol_version = 'HTTP/1.1'

    def log_message(self, *args):
        pass

    def record(self):
        log.append({'side': self.side, 'method': self.command, 'path': self.path,
                    'authorization': self.headers.get('Authorization'), 'cookie': self.headers.get('Cookie')})

    def body(self):
        n = int(self.headers.get('Content-Length') or 0)
        return self.rfile.read(n) if n else None

    def send(self, status, headers=(), body=b''):
        self.send_response(status)
        for k, v in headers:
            self.send_header(k, v)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        if self.command != 'HEAD':
            self.wfile.write(body)

    def control(self):
        if self.path == '/.sso/log':
            self.send(200, [('Content-Type', 'application/json')], json.dumps(log).encode())
        elif self.path == '/.sso/tokens':
            self.send(200, [('Content-Type', 'application/json')], json.dumps(tokens).encode())
        elif self.path == '/.sso/revoke':
            sessions.clear()
            self.send(204)
        elif self.path.startswith('/.sso/mode/'):
            state['direct'] = self.path.endswith('/direct')
            self.send(204)
        else:
            return False
        self.body()
        return True


class Proxy(Handler):
    side = 'proxy'

    def handle_any(self):
        if self.control():
            return
        self.record()
        url = urllib.parse.urlsplit(self.path)
        if url.path == '/.sso/callback':
            query = urllib.parse.parse_qs(url.query)
            code = query.get('code', [''])[0]
            if code in codes:
                codes.discard(code)
                token = secrets.token_hex(16)
                sessions.add(token)
                tokens.append(token)
                self.send(302, [('Set-Cookie', f'{COOKIE}={token}; Path=/; HttpOnly'),
                                ('Location', query.get('to', [PROXY + '/'])[0])])
            else:
                self.send(403, [('Content-Type', 'text/plain')], b'bad code')
            return
        jar = http.cookies.SimpleCookie()
        try:
            jar.load(self.headers.get('Cookie', ''))
        except http.cookies.CookieError:
            pass
        if COOKIE not in jar or jar[COOKIE].value not in sessions:
            self.body()
            if state['direct']:
                self.send(200, [('Content-Type', 'text/html; charset=utf-8')], page(PROXY + self.path))
            else:
                self.send(302, [('Location', f'{SIGN_IN}/authorize?to={urllib.parse.quote(PROXY + self.path, safe="")}')])
            return
        headers = {k: v for k, v in self.headers.items() if k.lower() not in ('host', 'connection')}
        request = urllib.request.Request(GERRIT + self.path, data=self.body(), headers=headers, method=self.command)
        try:
            response = forward.open(request)
        except urllib.error.HTTPError as e:
            response = e
        out = []
        for k, v in response.headers.items():
            if k.lower() not in ('transfer-encoding', 'connection', 'content-length'):
                out.append((k, v.replace(GERRIT, PROXY) if k.lower() == 'location' else v))
        self.send(response.status if hasattr(response, 'status') else response.code, out, response.read())

    do_GET = do_POST = do_PUT = do_DELETE = do_HEAD = handle_any


class SignIn(Handler):
    side = 'sign-in'

    def do_GET(self):
        if self.control():
            return
        self.record()
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query)
        self.send(200, [('Content-Type', 'text/html; charset=utf-8')], page(query.get('to', [PROXY + '/'])[0]))

    def do_POST(self):
        if self.control():
            return
        self.record()
        form = urllib.parse.parse_qs((self.body() or b'').decode())
        to = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query).get('to', [PROXY + '/'])[0]
        if [form.get(k, [''])[0] for k in ('user', 'password', 'code')] != [USER, PASSWORD, CODE]:
            self.send(401, [('Content-Type', 'text/html; charset=utf-8')], b'<h1>Wrong user, password or code</h1>')
            return
        code = secrets.token_hex(8)
        codes.add(code)
        self.send(302, [('Location', f'{PROXY}/.sso/callback?{urllib.parse.urlencode({"code": code, "to": to})}')])


if __name__ == '__main__':
    servers = [http.server.ThreadingHTTPServer(('127.0.0.1', 8090), Proxy),
               http.server.ThreadingHTTPServer(('127.0.0.1', 8091), SignIn)]
    for server in servers:
        threading.Thread(target=server.serve_forever, daemon=True).start()
    print('SSO proxy on', PROXY, 'sign-in page on', SIGN_IN, flush=True)
    threading.Event().wait()
