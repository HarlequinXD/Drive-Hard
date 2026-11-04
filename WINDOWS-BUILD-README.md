# Drive Hard - Lightweight Windows Build

This distribution contains the game JAR, JNA dependency, and a tiny native Windows EXE launcher. It deliberately does **not** bundle the Java runtime.

**Requirement for players:** install Java 17 or newer, and make sure `javaw.exe` is on PATH.

GitHub Actions builds on push to main and uploads one artifact containing DriveHard.exe, DriveHard.jar and lib/jna-5.16.0.jar. No nested ZIP is created by the workflow.

Run DriveHard.exe, or run `java -jar DriveHard.jar`. The EXE is a launcher; the game is still Java and uses its existing renderer.

To distribute a game that runs without Java installed, a Java runtime must be bundled or the application must be natively compiled, which needs further porting/testing.
