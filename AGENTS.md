# Windows builds

- Use the project wrapper (`.\gradlew.bat`) to build and test. It honors
  `JAVA_HOME` and Java on `PATH`, then falls back to `STUDIO_JDK` or the JDK
  bundled with Android Studio in its standard Windows installation locations.
- On this machine, the restricted execution environment cannot access the
  Android Studio JDK under `C:\Program Files\Android\Android Studio\jbr`.
  Java was verified to work outside that environment. Run Java/Gradle checks
  through `exec_command` with `sandbox_permissions: "require_escalated"`,
  subject to the normal approval policy. Do not disable sandboxing or change
  filesystem permissions. If approval is denied, report that limitation.
- Do not stop at a sandbox `Access denied` or missing `JAVA_HOME` error:
  use the wrapper with the approved execution mode and verify the actual build.
- A focused check for shared UI changes is
  `.\gradlew.bat :shared:compileKotlinJvm --offline`.
  Missing offline dependencies are a separate issue from Java access.
