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
2. Go to **Settings** (or **Preferences** on macOS) $\rightarrow$ **Plugins**.
3. ~~Search for `Linden Script (LSL)` under the **Marketplace** tab~~, or click the **⚙️ icon** $\rightarrow$ **Install Plugin from Disk...** to use a downloaded `.zip` from [GitHub Releases](https://github.com/KoolLSL/lsl-intellij/releases).
4. Click **Install** and restart your IDE if prompted.

### Quick Start

1. **Create a Project:** Open your IDE, go to **File $\rightarrow$ New $\rightarrow$ Project...**, select **Linden Script (LSL)**, and click **Create**.
2. **Add Your Source Files:**
    * **`.lslp` (Preprocessed File):** Create your main script here. It can use `#include *.lslm`, `#inline`, or `#ifdef` directives.
    * **`.lslm` (Module File):** Optional shared library files containing functions or constants to reuse across scripts.
3. **Build:** Save your `.lslp` file (`Ctrl+S`). The plugin automatically generates an optimized, read-only **`.lsl`** script in the `build/` folder.

4. **Import into Second Life:** You can copy and paste the generated `.lsl` text into your viewer script editor. If you use the **Firestorm Viewer**, you can also use its  `#include` feature to automatically get the generated `.lsl` file directly from your disk and recompile in-world.

---

### Issues & Feedback

> **Note:** This plugin is a personal side project and may not be 100% perfect. If you run into obvious issues, please report them on [GitHub Issues](https://github.com/KoolLSL/lsl-intellij/issues).

This project is a modernized fork of the original [riej/lsl](https://github.com/riej/lsl) plugin.

Compared to Eclipse/LSLForge, it has no internal simulator execution.

See [official LSL documentation](https://wiki.secondlife.com/wiki/LSL_Portal).


<sub style="color: #6a737d;">
Second Life (SL) and the Second Life Eye-in-Hand Logo are registered trademarks of Linden Research, Inc. This plugin is an independent third-party project and is not affiliated with, supported by, or endorsed by Linden Research, Inc.
</sub>
