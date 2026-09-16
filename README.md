# CL Calc — OLED basic Edition

A small [clcalc.net](https://clcalc.net/)-style command-line calculator for Android, written
in Kotlin. It looks and works like a classic UNIX terminal: you type an expression at the
`>` prompt, hit enter, and get the result.

Pure **OLED black & white** theme (true `#000000` / `#FFFFFF`, no grays, no colors), with an
optional white-on-black ↔ black-on-white toggle.

## Features

- **Terminal UI** with scrollable history
- **Arrow keys** on the keypad: `↑`/`↓` recall previous commands, `←`/`→` move the cursor to edit them
- **Basic math only** — no matrices, colors, plots or base64
- **On-screen keypad** plus a software keyboard for typing commands
- Commands: `help`, `clear` / `cls`, `theme`, `version`, `about`

### Operators & constants

| | |
|---|---|
| Arithmetic | `+  -  *  /  ^  !` |
| Percent | `a + b%` = `a + a*b/100`; `a * b%` = `a * b/100`, e.g. `50 + 3%` → `51.5`, `200 * 10%` → `20` |
| Factorial | `!`, e.g. `5!` → `120` |
| Constants | `pi  e  phi  tau  ans` (last result) |
| Implicit multiply | `2pi` = `2*pi`, `2(3+4)` = `14` |

### Functions (radians for trig)

`sqrt  cbrt  abs  sign  round  floor  ceil  trunc  frac`
`exp  ln  log  log2  pow  hypot  atan2`
`min  max  mod  gcd  lcm  fact`
`sin  cos  tan  asin  acos  atan  sec  csc  cot`
`rand()  rand(n)  rand(lo, hi)`

### Examples

```
> (2 + 3) * 5
25
> 2^10
1024
> sqrt(2)
1.414213562373
> 12/5 + 3
5.4
> ans * 2
10.8
```

## Build

Requires JDK 17+, Android SDK (compileSdk 35, minSdk 26) and Gradle 8.11.1.

```
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

The app has **zero third-party dependencies** — pure Android framework + Kotlin stdlib.

## Install

```
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Disclaimer

Floating-point arithmetic, so tiny rounding errors are possible. This is a basic calculator,
not a scientific tool — double-check important results.