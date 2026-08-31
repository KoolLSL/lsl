[![Version](https://img.shields.io/jetbrains/plugin/v/21002)](https://plugins.jetbrains.com/plugin/21002-linden-script-lsl-)
![GitHub](https://img.shields.io/github/license/koollsl/lsl)


Write LSL (Linden Script Language) directly inside IntelliJ IDEA, PyCharm, Android Studio, and other JetBrains editors.

### Key Features

* **Project & File Organization:** Manage large multi-file projects using structured project views, shared library, local history, file comparison, GitHub integration, and more.
* **Advanced Preprocessor:** Use file inclusions (`#include`), function inlining (`#inline`), and conditional blocks (`#ifdef`, `#ifndef`, `#else`, `#endif`) to organize large script projects.
* **Memory Optimization:** Built-in constant optimization evaluates static math, replaces fixed variables, and eliminates dead code before compiling — keeping your script's memory footprint as small as possible in Second Life.
* **LSL Database:** Use the popular [kwdb.xml](https://github.com/Sei-Lisa/kwdb) from Sei-Lisa for the definition of functions, constants, and events. When new LSL functions are released, you can simply download or edit the XML file yourself without waiting for a plugin update!
* **Code Formatting & Clean Up:** Automatically format your code, fix indentation, and keep your scripts clean and readable.
* **Smart Scripting Tools:** Instant syntax highlighting, real-time error checking, smart auto-completion, and safe variable/function refactoring.

### How to Install

1. Open your JetBrains IDE (IntelliJ IDEA, PyCharm, Android Studio, etc.).
2. Go to **Settings** → **Plugins**.
3. ~~Search for `Linden Script (LSL)` under the **Marketplace** tab~~, or click the **⚙️ icon** → **Install Plugin from Disk...** to use a downloaded `.zip` from [GitHub Releases](https://github.com/KoolLSL/lsl/releases).
4. Click **Install** and restart your IDE if prompted.

### Quick Start

1. **Create a Project:** Open your IDE, go to **File → New → Project...**, select **Linden Script (LSL)**, and click **Create**.
2. **Add Your Source Files:**
    * **`.lslp` (Preprocessed File):** Create your main script here (e.g. `MyScript.lslp`). It can use `#include`, `#inline`, or `#ifdef` directives.
    * **`.lslm` (Module File):** Optional. Shared library files containing functions or constants to reuse across scripts (e.g. via `#include "MyLib.lslm"` inside `MyScript.lslp`).
3. **Build:** Save your `.lslp` file (`Ctrl+S`). The plugin automatically generates an optimized, read-only **`.lsl`** script in the `/build` folder (e.g. `/build/MyScript.lsl`).

4. **Import into Second Life:** If you have the **Firestorm Viewer**, enable its LSL preprocessor to automatically get the generated `.lsl` from your local disk when you recompile in-world (e.g. in your in-world script, write only `#include "MyProject/build/MyScript.lsl"`). Alternatively, you can copy and paste the generated `.lsl` text into your viewer script editor.

---

### Issues & Feedback

> This plugin is a personal side project and may not be 100% perfect. If you run into obvious issues, please report them on [GitHub Issues](https://github.com/KoolLSL/lsl/issues).

This project is a modernized fork of the original [riej/lsl](https://github.com/riej/lsl) plugin.

Compared to the Eclipse/LSLForge, this plugin has no internal simulator or compiler, but offers preprocessing and syntax checking inside the more modern JetBrains IDEs. This also makes the plugin easier to install and maintain.

See [official LSL documentation](https://wiki.secondlife.com/wiki/LSL_Portal).


<sub style="color: #6a737d;">
Second Life (SL) and the Second Life Eye-in-Hand Logo are registered trademarks of Linden Research, Inc. This plugin is an independent third-party project and is not affiliated with, supported by, or endorsed by Linden Research, Inc.
</sub>
