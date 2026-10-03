# The rules R8 shrinks the releaseSmoke build's test APK with. The test runner is annotated with Error Prone's
# annotations, which only a compiler reads and which nothing on the test APK's classpath brings along any more (CameraX
# did, to the application).
-dontwarn com.google.errorprone.annotations.**
