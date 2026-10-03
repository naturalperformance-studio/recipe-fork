# R8 keep rules of the release build, the build the owner installs by hand. The libraries' own consumer rules come
# first (Room, kotlinx.serialization, Compose, AndroidX, Ktor); a rule is added here only where the minified
# smoke test (scripts/device-test-release.sh, ReleaseSmokeTest) proves one necessary, each with a line naming why.
#
# None is so far: the smoke test passes on the release rules alone.
