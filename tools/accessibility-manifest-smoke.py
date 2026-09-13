from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID = '{http://schemas.android.com/apk/res/android}'
manifest_path = Path('app/src/main/AndroidManifest.xml')
root = ET.parse(manifest_path).getroot()

service = None
for node in root.findall('./application/service'):
    if node.attrib.get(ANDROID + 'name') == '.automation.WaAccessibilityService':
        service = node
        break

if service is None:
    raise SystemExit('AccessibilityManifestSmoke: FAIL - WaAccessibilityService missing')

if service.attrib.get(ANDROID + 'permission') != 'android.permission.BIND_ACCESSIBILITY_SERVICE':
    raise SystemExit('AccessibilityManifestSmoke: FAIL - BIND_ACCESSIBILITY_SERVICE permission missing')

if service.attrib.get(ANDROID + 'exported') != 'true':
    raise SystemExit('AccessibilityManifestSmoke: FAIL - accessibility service must be android:exported="true" for system binding')

actions = {
    action.attrib.get(ANDROID + 'name')
    for intent_filter in service.findall('intent-filter')
    for action in intent_filter.findall('action')
}
if 'android.accessibilityservice.AccessibilityService' not in actions:
    raise SystemExit('AccessibilityManifestSmoke: FAIL - accessibility intent action missing')

meta = service.find('meta-data')
if meta is None or meta.attrib.get(ANDROID + 'resource') != '@xml/accessibility_service_config':
    raise SystemExit('AccessibilityManifestSmoke: FAIL - accessibility metadata missing')

print('AccessibilityManifestSmoke: PASS')
