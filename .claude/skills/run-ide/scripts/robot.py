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
"""Drives the IDE through the Remote Robot server plugin which ide.sh installs.

Components are found by XPath over the Swing tree, as `tree` prints it, e.g.
//div[contains(@class,'StripeButton') and @accessiblename='Gerrit'] (match classes with contains():
StripeButton became SquareStripeButton in newer IDEs).

  robot.py login [URL LOGIN PASSWORD]  that account on the Gerrit settings page, added or edited, and used by the
                                       project, which uses Gerrit (default: the seeded Gerrit)
  robot.py settings                    the Gerrit settings page of the project
  robot.py focus                       the IDE frame, for keys to reach it
  robot.py tree [WORD]                 visible components, indented; only lines with WORD
  robot.py find XPATH                  id, class and screen bounds of each showing match
  robot.py wait XPATH [SECONDS]        until XPATH matches (default 30 s)
  robot.py click XPATH [--right|--double]   the first match; (XPATH)[2] for the second
  robot.py check XPATH on|off          a check box, clicked only when it is not in that state yet
  robot.py item XPATH TEXT [--right|--double]   the row of a tree, list or table showing TEXT
  robot.py type TEXT                   into the focused component
  robot.py key KEY...                  e.g. ENTER, ESCAPE, ctrl+alt+S, shift+TAB
  robot.py get XPATH EXPR              a JavaScript expression over `component`, as text
  robot.py rows XPATH                  the rows of a tree, list or table, a table's cells tab separated
"""
import html.parser
import json
import sys
import time
import urllib.error
import urllib.request

URL = 'http://127.0.0.1:8082'  # -Drobot-server.port in ide.vmoptions
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))  # never through an http_proxy
FRAME = "//div[@class='IdeFrameImpl']"
ARGUMENTS = {'find': 1, 'wait': 1, 'click': 1, 'check': 2, 'item': 2, 'type': 1, 'key': 1, 'get': 2, 'rows': 1}


class Failure(Exception):
    pass


def call(path, body=None):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(URL + path, data, {'Content-Type': 'application/json'})
    try:
        with OPENER.open(request, timeout=120) as response:
            text = response.read().decode()
    except urllib.error.HTTPError as e:
        raise Failure('%s: HTTP %d %s %.300s' % (path, e.code, e.reason, e.read().decode(errors='replace')))
    except OSError as e:  # refused, reset or timed out
        raise Failure('robot server on %s: %s (is the IDE up? ide.sh start)' % (URL, e))
    if body is None:
        return text
    try:
        answer = json.loads(text)
    except ValueError:
        raise Failure('%s: not the robot server on %s? %.80s' % (path, URL, text))
    if answer.get('status') != 'SUCCESS':
        raise Failure('%s failed: %s' % (path, answer.get('message') or answer.get('log') or answer))
    return answer


def find(xpath):  # the server only matches components which are showing
    return call('/xpath/components', {'xpath': xpath})['elementList']


def one(xpath):
    found = find(xpath)
    if not found:
        raise Failure('no match for ' + xpath)
    return found[0]['id']


def run(script, component=None):
    call(('/%s' % component if component else '') + '/js/execute', {'script': script, 'runInEdt': False})


def retrieve(script, component=None):
    """The value of a script, which the server returns as a serialized Java object."""
    raw = bytes(b & 0xff for b in call(('/%s' % component if component else '') + '/js/retrieveAny',
                                       {'script': 'String(%s)' % script, 'runInEdt': True})['bytes'])
    if len(raw) < 5:
        raise Failure('no value from ' + script)
    # ObjectOutputStream header, then TC_STRING with a 2-byte or TC_LONGSTRING with an 8-byte length
    if raw[4] == 0x74:
        text = raw[7:7 + int.from_bytes(raw[5:7], 'big')]
    elif raw[4] == 0x7c:
        text = raw[13:13 + int.from_bytes(raw[5:13], 'big')]
    else:
        raise Failure('%s gave no text (serialized type 0x%02x)' % (script, raw[4]))
    # Java's modified UTF-8 writes NUL as two bytes and a character beyond the BMP as two surrogates
    return text.replace(b'\xc0\x80', b'\x00').decode('utf-8', 'surrogatepass').encode('utf-16', 'surrogatepass').decode('utf-16', 'replace')


class Tree(html.parser.HTMLParser):
    SHOWN = ('accessiblename', 'visible_text', 'tooltiptext')

    def __init__(self, word):
        super().__init__()
        self.word, self.depth, self.lines, self.hidden = word, 0, [], None

    def handle_starttag(self, tag, attrs):
        if tag != 'div':
            return
        self.depth += 1
        a = dict(attrs)
        if self.hidden is None and a.get('visible') == 'false':
            self.hidden = self.depth  # its children say visible as well, but are not shown
        if self.hidden is not None or 'class' not in a:
            return
        name = a.get('accessiblename')
        parts = ['%s=%r' % (k, a[k]) for k in self.SHOWN
                 if a.get(k) and (k == 'accessiblename' or a[k] != name)]
        line = '%s%s %s' % ('  ' * (self.depth - 1), a['class'], ' '.join(parts))
        if not self.word or self.word.lower() in line.lower():
            self.lines.append(line.rstrip())

    def handle_endtag(self, tag):
        if tag == 'div':
            if self.hidden == self.depth:
                self.hidden = None
            self.depth -= 1


def key_script(spec):
    # held down around the key: Rhino does not hand pressAndReleaseKey's modifier varargs through
    *modifiers, key = spec.split('+')
    alias = lambda k: {'ctrl': 'CONTROL'}.get(k.lower(), k.upper())
    names = ['VK_' + alias(m) for m in modifiers]
    # whatever was pressed is released even when a name is wrong, or every later key would carry it
    return '''var held = [], keys = java.awt.event.KeyEvent, code = function (name) {
            try { return keys[name]; } catch (e) { throw 'no key ' + name; }
        };
        try {
            %s.forEach(function (name) { robot.pressKey(code(name)); held.unshift(code(name)); });
            robot.pressAndReleaseKey(code(%s));
        } finally {
            held.forEach(function (c) { robot.releaseKey(c); });
        }''' % (json.dumps(names), json.dumps('VK_' + alias(key)))


ITEMS = '''function items() {  // text, bounds and row of each item; a table cell is an item of its own
    var out = [];
    if (component.getRowBounds) {
        for (var i = 0; i < component.getRowCount(); i++)
            out.push([String(component.getPathForRow(i).getLastPathComponent()), component.getRowBounds(i), i]);
    } else if (component.getCellRect) {
        for (var r = 0; r < component.getRowCount(); r++)
            for (var c = 0; c < component.getColumnCount(); c++)
                out.push([String(component.getValueAt(r, c)), component.getCellRect(r, c, true), r]);
    } else {
        for (var i = 0; i < component.getModel().getSize(); i++)
            out.push([String(component.getModel().getElementAt(i)), component.getCellBounds(i, i), i]);
    }
    return out;
}
'''

ROWS = '''(function () {  // inside the function, as retrieve() wraps the script in an expression
''' + ITEMS + '''    var rows = [];
    items().forEach(function (item) { rows[item[2]] = (rows[item[2]] == null ? '' : rows[item[2]] + '\\t') + item[0]; });
    return rows.join('\\n');
})()'''

# No window manager keeps a dialog above its frame, which the IDE raises once it has opened the
# project, and a click lands on whatever is on top.
RAISE = 'javax.swing.SwingUtilities.getWindowAncestor(component).toFront();'

ITEM = ITEMS + '''var at = null;
javax.swing.SwingUtilities.invokeAndWait(function () {  // the model may be changing, a refresh for one
    at = items().filter(function (item) { return item[0] == %s; })[0];
    if (at != null) { component.scrollRectToVisible(at[1]); ''' + RAISE + ''' }
});
if (at == null) throw 'no item';
// smooth scrolling takes a moment; a row wider than the view is clicked in its visible part
var shown = component.getVisibleRect().intersection(at[1]);
for (var t = 0; t < 20 && shown.height < at[1].height; t++) {
    java.lang.Thread.sleep(100);
    shown = component.getVisibleRect().intersection(at[1]);
}
if (shown.isEmpty()) throw 'item not on screen';
robot.click(component, new java.awt.Point(shown.x + shown.width / 2, shown.y + shown.height / 2), %s);'''


def until(check, seconds, what):
    deadline, last = time.time() + seconds, None
    while True:
        try:  # a query can fail while the components it walks are being disposed
            if check():
                return
        except Failure as e:
            if 'robot server on' in str(e):
                raise
            last = e
        if time.time() > deadline:
            raise Failure('timed out waiting for %s%s' % (what, '; last: %s' % last if last else ''))
        time.sleep(0.5)


def before_click(action, *failures):  # retries only these: anything else may have clicked already
    deadline = time.time() + 10
    while True:
        try:
            return action()
        except Failure as e:
            if not any(f in str(e) for f in failures) or time.time() > deadline:
                raise
            time.sleep(1)


ACCOUNTS = "//div[@accessiblename='Gerrit Accounts']//div[@class='JBList']"


def settings():
    # by name, as 2026.2's settings search drops or ignores what is typed while the pages load
    run('''javax.swing.SwingUtilities.invokeLater(function () {
        var project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
        com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(project, 'Gerrit');
    });''')
    until(lambda: find(ACCOUNTS), 60, 'the Gerrit settings page')  # a first start may still open the project


def login(url='http://localhost:8080', user='admin', password='secret'):
    settings()
    # the account of this instance and login is edited rather than added a second time
    account = '%s@%s' % (user, url)
    if account in retrieve(ROWS, one(ACCOUNTS)).split('\n'):
        main(['item', ACCOUNTS, account, '--double'])
    else:
        main(['click', "//div[@class='ActionButton' and @accessiblename='Add']"])
    dialog = "//div[@class='MyDialog' and contains(@accessiblename,'Gerrit Account')]"
    until(lambda: find(dialog), 30, 'the account dialog')
    for field, value in (("@class='JBTextField' and @accessiblename='Web URL:'", url),
                         ("@class='JBTextField' and @accessiblename='Login:'", user),
                         ("@class='JPasswordField'", password)):
        main(['click', '%s//div[%s]' % (dialog, field)])
        main(['key', 'ctrl+A'])  # typing replaces what a previous login left
        main(['type', value])
    main(['click', "%s//div[@class='JButton' and @accessiblename='OK']" % dialog])
    until(lambda: not find(dialog), 30, 'the account dialog to close')
    # the account just added or edited is the selected one; the project uses it from now on
    main(['click', "//div[@class='ActionButton' and @accessiblename='Use for This Project']"])
    main(['check', "//div[@class='JCheckBox' and @accessiblename='Use Gerrit in this project']", 'on'])
    page = "//div[@accessiblename='Gerrit Accounts']"
    main(['click', "//div[@class='JButton' and @accessiblename='OK']"])
    until(lambda: not find(page), 30, 'the settings dialog to close; did OK not save?')  # a busy IDE takes a while


def how(options):
    button, times = ('RIGHT', 1) if '--right' in options else ('LEFT', 2 if '--double' in options else 1)
    return 'Packages.org.assertj.swing.core.MouseButton.%s_BUTTON, %d' % (button, times)


def click(options):
    return 'javax.swing.SwingUtilities.invokeAndWait(function () { %s });\nrobot.click(component, %s)' % (
        RAISE, how(options))


def main(args):
    if not args:
        sys.exit(__doc__)
    command, rest = args[0], args[1:]
    if len(rest) < ARGUMENTS.get(command, 0) or command == 'login' and len(rest) > 3:
        sys.exit(__doc__)
    if command == 'focus':  # a click would land on whatever lies there, and the keys go to the frame anyway
        run('''javax.swing.SwingUtilities.invokeAndWait(function () {
            component.toFront(); component.requestFocus(); });''', one(FRAME))
        time.sleep(0.5)
    elif command == 'login':
        login(*rest)
    elif command == 'settings':
        settings()
    elif command == 'tree':
        tree = Tree(rest[0] if rest else None)
        tree.feed(call('/hierarchy'))
        print('\n'.join(tree.lines))
    elif command == 'find':
        for c in find(rest[0]):
            print('%s\t%s\t%d,%d %dx%d' % (c['id'], c['className'], c['x'], c['y'], c['width'], c['height']))
    elif command == 'wait':
        try:
            seconds = float(rest[1] if len(rest) > 1 else 30)
        except ValueError:
            raise Failure('wait takes seconds, not ' + rest[1])
        until(lambda: find(rest[0]), seconds, rest[0])
    elif command == 'click':  # a toolbar may replace its buttons, the one just found among them
        before_click(lambda: run(click(rest), one(rest[0])), 'no match', 'must be showing')
    elif command == 'check':
        if rest[1] not in ('on', 'off'):
            raise Failure('check takes on or off, not ' + rest[1])
        box = one(rest[0])  # check boxes keep their state, the push dialog's per project for one
        state = retrieve('component.isSelected()', box)
        if state not in ('true', 'false'):
            raise Failure('cannot tell whether %s is checked: %r' % (rest[0], state))
        want = 'true' if rest[1] == 'on' else 'false'
        if state != want:
            run(click(()), box)
            if retrieve('component.isSelected()', box) != want:  # disabled, or not laid out yet
                raise Failure('%s did not turn %s' % (rest[0], rest[1]))
    elif command == 'item':  # lists fill in late
        before_click(lambda: run(ITEM % (json.dumps(rest[1]), how(rest)), one(rest[0])),
                     'no item', 'no match', 'not on screen')
    elif command == 'type':
        run('robot.enterText(%s)' % json.dumps(rest[0]))
    elif command == 'key':
        for spec in rest:
            run(key_script(spec))
    elif command == 'get':
        print(retrieve(rest[1], one(rest[0])))
    elif command == 'rows':
        print(retrieve(ROWS, one(rest[0])))
    else:
        sys.exit(__doc__)


if __name__ == '__main__':
    try:
        main(sys.argv[1:])
    except Failure as e:
        sys.exit(str(e))
