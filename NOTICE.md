# Third-party notices

Precise Player is licensed under the GNU General Public License v3 (see LICENSE).

It bundles a build of **mpv** (GPL-2.0-or-later) and **FFmpeg** (GPL), plus libass, FriBidi,
HarfBuzz, FreeType, fontconfig, dav1d, libplacebo, Mbed TLS, curl, libxml2 and Lua, built with the
build scripts of **mpv-android**. Source for all of these is fetched from their upstream projects by
`.github/workflows/native.yml` at the versions listed in mpv-android's `buildscripts/include/depinfo.sh`
(mpv-android commit pinned in that workflow).

`app/src/main/java/is/xyz/mpv/MPVLib.kt` and the JNI library `libplayer.so` come from mpv-android:

```
Copyright (c) 2016 Ilya Zhuravlev
Copyright (c) 2016 sfan5 <sfan5@live.de>

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES
OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

AndroidX, Jetpack Compose, Material icons and Kotlin are under the Apache License 2.0.
