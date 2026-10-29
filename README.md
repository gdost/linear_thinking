# Water Glass – a game about linear vs. exponential thinking

A glass holds a small random amount of water (say 15 ml).

1. **Linear round:** "Where would 18 × this amount reach?" Tap a height in the glass, then watch it fill to the true level.
2. **Doubling round:** "Where would 2¹⁸ × this amount reach?" Tap again. The water doubles step by step, overflows the glass,
   the camera zooms out to a giant bowl (with a 1.75 m person for scale) and the bowl fills up.
   All sizes are drawn to scale (glass = 400 ml cylinder, bowl sized so the final amount fills it to ~70 %).

## Build

Plain Java + Android Canvas, no Gradle or AndroidX. Needs aapt, dx (`dalvik-exchange`), zipalign, apksigner and an
`android-23` platform jar, e.g. on Ubuntu:

    apt-get install android-sdk android-sdk-platform-23 dalvik-exchange
    ./build.sh      # -> build/water-glass-debug.apk

Install on a phone: copy the APK over and open it (allow "install unknown apps"), or `adb install build/water-glass-debug.apk`.

## Download

Latest debug APK: [apk/water-glass.apk](https://github.com/gdost/linear_thinking/raw/main/apk/water-glass.apk)
