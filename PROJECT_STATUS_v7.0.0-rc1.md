# Project Status — v7.0.0-rc1

## Proven locally
- Core policy/link/import/export tests pass.
- Android resource XML is structurally valid.
- Required features remain in source.
- Accessibility service manifest configuration passes the project smoke gate.
- No fixed multi-second navigation sleeps are present.
- Sync/extraction navigation policies are deterministic and tested.

## Not proven in this environment
- `:app:testDebugUnitTest`
- `:app:lintDebug`
- `:app:assembleDebug`
- Physical-device WhatsApp UI compatibility.

These are delegated to the included GitHub Actions workflow and the device test below.
