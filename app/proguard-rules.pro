# R8 keep rules of the release build, the build the owner installs by hand. The libraries' own consumer rules come
# first (Room, kotlinx.serialization, Compose, AndroidX, Ktor, Media3); a rule is added here only where the minified
# smoke test (scripts/device-test-release.sh, ReleaseSmokeTest) proves one necessary, each with a line naming why.

# Media3 holds android.media.metrics types (API 31) in fields left null below that level. R8 merges such a class into
# another, widens the field and casts it back on reading, and the cast fails on API 29-30 for want of the class
# (ReleaseSmokeTest.theBuildConvertsAClip). Classes holding such a field are left unmerged; they may still be removed
# and renamed.
-keepclasseswithmembers,allowshrinking,allowobfuscation class androidx.media3.** {
    android.media.metrics.** *;
}
