from pathlib import Path
s = Path('app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt').read_text()
required = ['private fun clickTarget(', 'private fun longClickTarget(', 'dispatchGesture(', 'GestureDescription.Builder()', 'getBoundsInScreen']
missing = [x for x in required if x not in s]
if missing:
    raise SystemExit(f'GestureClickRegressionSmoke missing: {missing}')
print('GestureClickRegressionSmoke: PASS')
