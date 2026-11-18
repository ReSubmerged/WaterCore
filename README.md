<p align="center">
  <img src="logo.png" alt="Logo" width="300">
</p>


<h3 align="center">MangoCore</h3>
<h3 align="center">SusNetwork's Android launcher based on <a href="https://github.com/All-Of-Us-Mods/FusionCore">FusionCore</a></h3>

<p align="center">
  <img src="https://img.shields.io/github/stars/SusNetwork/MangoCore" alt="Stars">
  <img src="https://img.shields.io/badge/unity-%23000000.svg?logo=unity&logoColor=white" alt="Unity">
  <img src="https://img.shields.io/badge/Android-%233DDC84.svg?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/GitHub%20Actions-%232671E5.svg?logo=GitHubActions&logoColor=white" alt="GitHub Actions">
  <img src="https://img.shields.io/badge/Discord-%235865F2.svg?logo=discord&logoColor=white" alt="Discord">
</p>

MangoCore is an Among Us launcher that uses SusNetwork's servers. It supports mods & plugins.

Requires Android 7+ ; however, MangoCore hasn't been tested on versions lower than Android 12.

### Features

| Feature | State | Description |
| ----- | ----- | ----- |
| Mod shop | Work in progress | Allows you to download mods from an API instead of uploading your own DLLs. |
| SusNetwork | Stopped | SusNetwork is in active development; as soon as SusNetwork comes out, MangoCore will be updated. |

## Mods

You can upload custom DLLs to load plugins & mods. There is no security check: do not run or add untrusted mods.

You can also use the built-in mod Shop. You can also change the URL MangoCore uses to fetch the Shop's mods in the Settings.

## Build

Use the Gradle wrapper from the repository root:

```powershell
.\gradlew.bat :mangoApp:assembleDebug
```

Optional clean build:

```powershell
.\gradlew.bat clean :mangoApp:assembleDebug
```
