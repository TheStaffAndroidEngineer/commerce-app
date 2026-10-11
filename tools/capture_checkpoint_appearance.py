#!/usr/bin/env python3
"""Capture package-bound native viewports; never clear data or open labs implicitly.

Run only after the runtime/device queue releases the emulator. Each configuration
force-stops the app, starts a fresh task without clearing persistent data, applies
display settings, replays explicit actions, waits for
stable fresh UI/optional expectations, and records every bounded pane viewport.
Original display settings are restored independently in finally. Screenshots are
appearance evidence, not correctness assertions or representative performance.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'projects/catalog-network/tools'))
from fresh_android_xml import read_ui_xml


def bounds(node):
    value = list(map(int, re.findall(r'-?\d+', node.get('bounds', ''))))
    return tuple(value) if len(value) == 4 else (0, 0, 0, 0)


def area(rect):
    return max(0, rect[2] - rect[0]) * max(0, rect[3] - rect[1])


def intersection(first, second):
    return (max(first[0], second[0]), max(first[1], second[1]),
            min(first[2], second[2]), min(first[3], second[3]))


def matches(node, label):
    return any(node.get(key) == label for key in ('text', 'content-desc', 'resource-id'))


def navigation_guard_rect(block, frame):
    """Narrow an explicitly untouchable navigation container to its active strip."""
    # The taskbar can include transparent padding. Without an explicitly empty
    # touchable region, conservatively guard its whole observed window frame.
    if not re.search(r'(?m)^\s+touchable region=SkRegion\(\)\s*$', block):
        return frame
    # Rotation templates and IME-specific overrides are not the current provider.
    current_attrs = block.split('paramsForRotation:', 1)[0]
    providers = re.findall(
        r'(?m)^\s+InsetsFrameProvider: \{[^\n]*?\btype=navigationBars,[^\n]*$',
        current_attrs)
    if len(providers) != 1:
        return frame
    provider = providers[0].split('insetsSizeOverrides=', 1)[0]
    if not re.search(r'\bsource=FRAME,', provider):
        return frame
    size = re.search(r'\binsetsSize=Insets\{left=(\d+), top=(\d+), '
                     r'right=(\d+), bottom=(\d+)\}', provider)
    if size is None:
        return frame
    left, top, right, bottom = map(int, size.groups())
    if sum(value > 0 for value in (left, top, right, bottom)) != 1:
        return frame
    x, y, end_x, end_y = frame
    if left > end_x - x or right > end_x - x or top > end_y - y or bottom > end_y - y:
        return frame
    if left:
        return (x, y, x + left, end_y)
    if top:
        return (x, y, end_x, y + top)
    if right:
        return (end_x - right, y, end_x, end_y)
    return (x, end_y - bottom, end_x, end_y)


def system_bar_regions(window_dump):
    """Read actual visible bar window frames, not rotation defaults or guessed dp."""
    regions = []
    recognized = False
    for block in re.split(r'(?m)^  Window #\d+ ', window_dump)[1:]:
        kind = re.search(r'\bty=(STATUS_BAR|NAVIGATION_BAR)\b', block)
        if not kind or not re.search(r'\bmDisplayId=0\b', block):
            continue
        recognized = True
        if not re.search(r'(?m)^\s+isVisible=true\s*$', block):
            continue
        frame = re.search(r'Frames:.*?\bframe=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', block)
        if frame is None:
            raise RuntimeError('Visible system bar has no current frame')
        rect = tuple(map(int, frame.groups()))
        if area(rect):
            if kind.group(1) == 'NAVIGATION_BAR':
                rect = navigation_guard_rect(block, rect)
            regions.append(rect)
    if not recognized:
        raise RuntimeError('Window dump has no recognized system bar windows; refusing unguarded taps')
    return regions


def click_target(tree, label, blocked_regions=()):
    """Use a fresh visible, enabled clickable ancestor; never guess a coordinate."""
    parents = {child: parent for parent in tree.iter() for child in parent}
    for node in tree.iter('node'):
        if not matches(node, label):
            continue
        target = node
        while target in parents and target.get('clickable') != 'true':
            target = parents[target]
        if target.get('clickable') != 'true' or target.get('enabled') != 'true':
            continue
        original = bounds(target)
        visible = original
        parent = target
        while parent in parents:
            parent = parents[parent]
            if parent.get('scrollable') == 'true':
                visible = intersection(visible, bounds(parent))
        # The pointer uses this exact center. A wide taskbar window can include
        # transparent padding that overlaps a button edge without covering its tap.
        center_x = (original[0] + original[2]) // 2
        center_y = (original[1] + original[3]) // 2
        blocked_pointer = any(left <= center_x < right and top <= center_y < bottom
                              for left, top, right, bottom in blocked_regions)
        if area(original) and visible == original and not blocked_pointer:
            return target
    return None


def panes(tree, package):
    return [node for node in tree.iter('node') if node.get('package') == package
            and node.get('scrollable') == 'true' and area(bounds(node))]


def pane_key(node):
    rect = bounds(node)
    return (node.get('resource-id', ''), node.get('class', ''), rect[0], rect[2])


def find_pane(tree, package, key):
    return next((node for node in panes(tree, package) if pane_key(node) == key), None)


def signature(node):
    fields = ('text', 'content-desc', 'resource-id', 'class', 'bounds', 'enabled',
              'checked', 'clickable', 'scrollable', 'selected')
    return tuple(tuple(item.get(field, '') for field in fields)
                 for item in node.iter('node'))


def app_signature(tree, package):
    return tuple(signature(node) for node in tree.findall('node')
                 if node.get('package') == package) or tuple(
        tuple(sorted(node.attrib.items())) for node in tree.iter('node')
        if node.get('package') == package)


def expectations_met(tree, exact, contains):
    nodes = list(tree.iter('node'))
    return (all(any(matches(node, value) for node in nodes) for value in exact)
            and all(any(value in node.get('text', '') for node in nodes) for value in contains))


class CaptureSession:
    def __init__(self, args):
        self.args = args
        self.adb_path = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk'))) / 'platform-tools/adb'
        self.commands = []
        self.tap_guards = []

    def adb(self, *args):
        result = subprocess.run([str(self.adb_path), '-s', self.args.serial, *map(str, args)],
                                capture_output=True, timeout=45)
        self.commands.append({'args': list(map(str, args)), 'exit': result.returncode})
        if result.returncode:
            raise RuntimeError(f'adb {args}: {result.stderr.decode(errors="replace").strip()}')
        return result.stdout

    def xml(self):
        return read_ui_xml(self.adb, '/sdcard/common-goods-appearance.xml',
                           {self.args.package}, attempts=2)

    def blocked_regions(self):
        raw = self.adb('shell', 'dumpsys', 'window', 'windows')
        regions = system_bar_regions(raw.decode(errors='replace'))
        digest = hashlib.sha256(raw).hexdigest()
        path = self.args.output / ('tap-window-' + digest[:16] + '.txt')
        if not path.exists():
            path.write_bytes(raw)
        self.tap_guards.append({'windowDump': path.name, 'sha256': digest,
                                'blockedRegions': regions})
        return regions

    def target(self, tree, label):
        return click_target(tree, label, self.blocked_regions())

    def settled(self, exact=(), contains=()):
        deadline = time.monotonic() + self.args.settle_timeout
        last = None
        error = None
        while time.monotonic() < deadline:
            try:
                raw = self.xml()
                tree = ET.fromstring(raw)
                current = app_signature(tree, self.args.package)
                if current == last and expectations_met(tree, exact, contains):
                    return raw
                last = current
            except Exception as caught:
                error = str(caught)
                last = None
            time.sleep(.25)
        raise RuntimeError(f'UI did not settle with expectations {list(exact)!r}/{list(contains)!r}; last read error: {error}')

    def swipe(self, pane, direction):
        x, y, right, bottom = bounds(pane)
        if not area((x, y, right, bottom)):
            raise RuntimeError('Fresh pane has no usable bounds')
        low, high = y + (bottom - y) * 4 // 5, y + (bottom - y) // 5
        start, end = (low, high) if direction == 'down' else (high, low)
        self.adb('shell', 'input', 'swipe', (x + right) // 2, start,
                 (x + right) // 2, end, 180)
        return self.settled()

    def seek(self, key, direction, label=None, raw=None):
        """Finite per-pane scan using fresh topology after every swipe."""
        raw = self.settled() if raw is None else raw
        steps = 0
        for _ in range(self.args.max_scrolls):
            tree = ET.fromstring(raw)
            if label and self.target(tree, label) is not None:
                return {'found': True, 'boundary': False, 'steps': steps}, raw
            pane = find_pane(tree, self.args.package, key)
            if pane is None:
                return {'found': False, 'boundary': True, 'steps': steps, 'pane_disappeared': True}, raw
            before = signature(pane)
            raw = self.swipe(pane, direction)
            tree = ET.fromstring(raw)
            steps += 1
            after = find_pane(tree, self.args.package, key)
            if label and self.target(tree, label) is not None:
                return {'found': True, 'boundary': False, 'steps': steps}, raw
            if after is None or signature(after) == before:
                return {'found': False, 'boundary': True, 'steps': steps}, raw
        return {'found': False, 'boundary': False, 'steps': steps, 'truncated': True}, raw

    def tap(self, label, raw=None):
        raw = self.settled() if raw is None else raw
        # Views navigation may remain in XML after scrolling under the status bar.
        # Rewind explicit destination actions before resolving a fresh target.
        if label.startswith('Open '):
            tree = ET.fromstring(raw)
            for key in [pane_key(node) for node in panes(tree, self.args.package)]:
                _, raw = self.seek(key, 'up', raw=raw)
        deadline = time.monotonic() + self.args.settle_timeout
        while time.monotonic() < deadline:
            tree = ET.fromstring(raw)
            target = self.target(tree, label)
            if target is None:
                keys = [pane_key(node) for node in panes(tree, self.args.package)]
                for key in keys:
                    _, raw = self.seek(key, 'up', label, raw=raw)
                    tree = ET.fromstring(raw)
                    target = self.target(tree, label)
                    if target is None:
                        _, raw = self.seek(key, 'down', label, raw=raw)
                        tree = ET.fromstring(raw)
                        target = self.target(tree, label)
                    if target is not None:
                        break
            if target is not None:
                x, y, right, bottom = bounds(target)
                self.adb('shell', 'input', 'tap', (x + right) // 2, (y + bottom) // 2)
                return self.settled()
            time.sleep(.25)
            raw = self.settled()
        raise RuntimeError('Visible enabled action unavailable after bounded bidirectional search: ' + label)

    def capture(self, name, raw=None):
        # A caller may pass the stable XML just obtained after its last action.
        # Capturing the screenshot does not mutate that viewport.
        raw = self.settled() if raw is None else raw
        tree = ET.fromstring(raw)
        png = self.adb('exec-out', 'screencap', '-p')
        xml_path = self.args.output / f'{name}.xml'
        image_path = self.args.output / f'{name}.png'
        xml_path.write_text(raw)
        image_path.write_bytes(png)
        return {'name': name, 'png': image_path.name, 'xml': xml_path.name,
                'pngSha256': hashlib.sha256(png).hexdigest(),
                'xmlSha256': hashlib.sha256(raw.encode()).hexdigest(),
                'texts': [node.get('text') for node in tree.iter('node') if node.get('text')],
                'panes': [{'key': list(pane_key(node)), 'bounds': list(bounds(node))}
                          for node in panes(tree, self.args.package)]}

    def capture_panes(self, config, raw=None):
        raw = self.settled() if raw is None else raw
        tree = ET.fromstring(raw)
        keys = [pane_key(node) for node in panes(tree, self.args.package)]
        rewinds = []
        for key in keys:
            result, raw = self.seek(key, 'up', raw=raw)
            rewinds.append({'key': list(key), **result})
        frames = [self.capture(config + '-top', raw=raw)]
        # Re-discover after rewinding: retain no stale element/bounds handles.
        tree = ET.fromstring(raw)
        keys = [pane_key(node) for node in panes(tree, self.args.package)]
        coverage = []
        for index, key in enumerate(keys, 1):
            seen = set()
            boundary = False
            for step in range(1, self.args.max_scrolls + 1):
                tree = ET.fromstring(raw)
                pane = find_pane(tree, self.args.package, key)
                if pane is None:
                    boundary = True
                    break
                before = signature(pane)
                raw = self.swipe(pane, 'down')
                tree = ET.fromstring(raw)
                after = find_pane(tree, self.args.package, key)
                current = signature(after) if after is not None else ()
                if current == before or current in seen:
                    boundary = True
                    break
                seen.add(current)
                frames.append(self.capture(f'{config}-pane{index}-viewport-{step:02d}', raw=raw))
            coverage.append({'key': list(key), 'stable_boundary_reached': boundary,
                             'limit_reached': not boundary, 'changed_viewports': len(seen)})
        return frames, rewinds, coverage

    def failure_witness(self):
        """Read the current viewport before cleanup, without retrying an action."""
        witness = {'scope': 'Fresh failure observation before app/display restoration; '
                            'not a settled successful viewport.', 'errors': {}}
        try:
            raw = read_ui_xml(self.adb, '/sdcard/common-goods-failure.xml',
                              {self.args.package, 'android', 'com.android.systemui'}, attempts=1)
            path = self.args.output / 'failure-viewport.xml'
            path.write_text(raw)
            witness['xml'] = path.name
            witness['xmlSha256'] = hashlib.sha256(raw.encode()).hexdigest()
        except Exception as error:
            witness['errors']['xml'] = str(error)
        try:
            png = self.adb('exec-out', 'screencap', '-p')
            path = self.args.output / 'failure-viewport.png'
            path.write_bytes(png)
            witness['png'] = path.name
            witness['pngSha256'] = hashlib.sha256(png).hexdigest()
        except Exception as error:
            witness['errors']['png'] = str(error)
        try:
            raw = self.adb('shell', 'dumpsys', 'window', 'windows')
            path = self.args.output / 'failure-window.txt'
            path.write_bytes(raw)
            witness['window'] = path.name
            witness['windowSha256'] = hashlib.sha256(raw).hexdigest()
        except Exception as error:
            witness['errors']['window'] = str(error)
        return witness

    def settings(self):
        return {'font': self.adb('shell', 'settings', 'get', 'system', 'font_scale').decode().strip(),
                'night': self.adb('shell', 'cmd', 'uimode', 'night').decode().strip(),
                'size': self.adb('shell', 'wm', 'size').decode().strip(),
                'density': self.adb('shell', 'wm', 'density').decode().strip()}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--package', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--screen', default='shopping')
    parser.add_argument('--action', action='append', default=[])
    parser.add_argument('--expect', action='append', default=[], help='Exact text/content-description/resource ID required across the captured viewports')
    parser.add_argument('--expect-contains', action='append', default=[])
    parser.add_argument('--configs', default='light,dark,large,wide')
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--expected-avd', default='StaffAndroid_API_36')
    parser.add_argument('--max-scrolls', type=int, default=8)
    parser.add_argument('--settle-timeout', type=float, default=45)
    args = parser.parse_args()
    configs = args.configs.split(',')
    if any(config not in ('light', 'dark', 'large', 'wide') for config in configs):
        parser.error('Configs must be light,dark,large,wide')
    if not 1 <= args.max_scrolls <= 20 or not 1 <= args.settle_timeout <= 120:
        parser.error('Invalid finite capture limits')
    if not args.apk.is_file():
        parser.error('APK does not exist')
    if args.output.exists() and any(args.output.iterdir()):
        parser.error('Use a new empty output directory; existing evidence will not be overwritten')
    args.output.mkdir(parents=True, exist_ok=True)
    session = CaptureSession(args)
    report = {'status': 'STARTING', 'package': args.package, 'serial': args.serial,
              'apk': str(args.apk.resolve()), 'apkSha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
              'screen': args.screen, 'actions': args.action, 'expect': args.expect,
              'expectContains': args.expect_contains, 'rows': [], 'restorationErrors': [],
              'scope': 'API36 emulator appearance; no automatic Lab expansion, no data clear, not physical-device performance evidence.'}
    original = None
    failure = None
    try:
        avd = session.adb('emu', 'avd', 'name').decode().strip()
        if args.expected_avd not in avd.splitlines():
            raise RuntimeError('Unexpected emulator: ' + avd)
        original = session.settings()
        report['originalSettings'] = original
        session.adb('install', '-r', args.apk)
        package = session.adb('shell', 'dumpsys', 'package', args.package).decode(errors='replace')
        report['installedVersion'] = [line.strip() for line in package.splitlines()
                                      if 'versionCode=' in line or 'versionName=' in line]
        for config in configs:
            row = {'config': config, 'status': 'STARTING'}
            report['rows'].append(row)
            session.adb('shell', 'am', 'force-stop', args.package)
            session.adb('shell', 'wm', 'size', '1920x1200' if config == 'wide' else 'reset')
            session.adb('shell', 'wm', 'density', '240' if config == 'wide' else 'reset')
            session.adb('shell', 'settings', 'put', 'system', 'font_scale', '2.0' if config == 'large' else '1.0')
            session.adb('shell', 'cmd', 'uimode', 'night', 'yes' if config == 'dark' else 'no')
            row['appliedSettings'] = session.settings()
            if float(row['appliedSettings']['font']) != (2.0 if config == 'large' else 1.0):
                raise RuntimeError('Requested font scale was not applied')
            expected_night = 'yes' if config == 'dark' else 'no'
            if not re.search(r'Night mode:\s*' + expected_night + r'\b', row['appliedSettings']['night'], re.I):
                raise RuntimeError('Requested night mode was not applied')
            if config == 'wide':
                if 'Override size: 1920x1200' not in row['appliedSettings']['size'] or 'Override density: 240' not in row['appliedSettings']['density']:
                    raise RuntimeError('Requested wide display was not applied')
            elif 'Override' in row['appliedSettings']['size'] or 'Override' in row['appliedSettings']['density']:
                raise RuntimeError('Base display reset was not applied')
            session.adb('shell', 'am', 'start', '-W', '-n', args.package + '/.MainActivity', '-f', '0x10008000')
            raw = session.settled()
            for action in args.action:
                raw = session.tap(action, raw=raw)
            row['frames'], row['rewinds'], row['paneCoverage'] = session.capture_panes(config, raw=raw)
            captured_tree = ET.Element('captured-viewports')
            for frame in row['frames']:
                captured_tree.append(ET.fromstring((args.output / frame['xml']).read_text()))
            row['expectationsMet'] = expectations_met(captured_tree, args.expect, args.expect_contains)
            if not row['expectationsMet']:
                raise RuntimeError('Required final-state text absent from finite captured viewports: ' + repr(args.expect) + '/' + repr(args.expect_contains))
            row['status'] = 'CAPTURED_NOT_REVIEWED'
            (args.output / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
        if hashlib.sha256(args.apk.read_bytes()).hexdigest() != report['apkSha256']:
            raise RuntimeError('APK changed during capture')
        report['status'] = 'CAPTURED_NOT_REVIEWED'
    except Exception as error:
        failure = error
        report['status'] = 'FAILED'
        report['error'] = str(error)
        # Preserve the failed configuration before force-stop or display reset.
        # Diagnostic collection must never replace the original failure.
        try:
            report['failureWitness'] = session.failure_witness()
        except Exception as witness_error:
            report['failureWitness'] = {'error': str(witness_error)}
    finally:
        restore = [('shell', 'am', 'force-stop', args.package)]
        if original:
            restore.append(('shell', 'settings', 'delete', 'system', 'font_scale') if original['font'] in ('null', '')
                           else ('shell', 'settings', 'put', 'system', 'font_scale', original['font']))
            restore.append(('shell', 'cmd', 'uimode', 'night', original['night'].split(':')[-1].strip()))
            for kind in ('size', 'density'):
                override = re.search(r'Override ' + kind + r':\s*(\S+)', original[kind], re.I)
                restore.append(('shell', 'wm', kind, override.group(1) if override else 'reset'))
        for command in restore:
            try:
                session.adb(*command)
            except Exception as error:
                report['restorationErrors'].append(str(error))
        if report['restorationErrors'] and not failure:
            report['status'] = 'CAPTURED_RESTORE_FAILED'
        (args.output / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
        (args.output / 'commands.json').write_text(json.dumps(session.commands, indent=2) + '\n')
        (args.output / 'tap-guards.json').write_text(json.dumps(session.tap_guards, indent=2) + '\n')
    if failure:
        raise failure
    if report['restorationErrors']:
        raise RuntimeError('Display restoration failed; inspect result.json')


if __name__ == '__main__':
    main()
