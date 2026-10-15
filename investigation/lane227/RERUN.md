# Re-run lane 227 checks

Use a fresh clone of https://github.com/CodexCoder21Organization/netty-2 in a lane directory named `netty-2`. Maven wrapper is included. Commands ran with JDK 21, `JAVA_OPTS=-Xmx2g MAVEN_OPTS=-Xmx2g JAVA_TOOL_OPTIONS=-Xmx2g`, a lane-private absolute `-Dmaven.repo.local=<lane>/m2`, and no more than one Maven command at a time.

Before each build: `git fetch origin` then `git rebase origin/4.2` (this repository has no main branch). All rebases were no-ops.

Dependency setup:

```
./mvnw -B -Dmaven.repo.local=<lane>/m2 -Dcheckstyle.skip -Dforbiddenapis.skip -Dxml.skip -Dnohttp.skip -pl transport -am -DskipTests install
```

Initial fail-first production source: https://github.com/CodexCoder21Organization/netty-2/commit/df533854c5 . Run `-pl transport -Dtest=NioShutdownReentryTest test` with the same Maven flags three times (after the initial compilation, `surefire:test` also works). Two callback cases fail and live-loop registration passes each time. Complete stdout and Surefire reports are in baseline-* artifacts.

Final five-test class against unchanged old production: https://github.com/CodexCoder21Organization/netty-2/commit/fa4b29676e . The same command discovers five cases: three fail (open channel after termination, registered channel after termination, same-thread registration accepted after immediate shutdown). Live-loop and post-termination caller-rejection preservation cases pass. Full output is baseline-terminal-*.

Corrected production and final tests: https://github.com/CodexCoder21Organization/netty-2/commit/7a9caa8cfd38683230db50ae83c58f22002044ba . Selected final-source six-case command (ten fresh-JVM invocations):

```
./mvnw -B -Dmaven.repo.local=<lane>/m2 -Dcheckstyle.skip -Dforbiddenapis.skip -Dxml.skip -Dnohttp.skip -pl transport '-Dtest=NioShutdownReentryTest,NioEventLoopTest#testShutdownFromTaskClosesRegisteredChannel+testShutdownFromCloseListenerClosesOtherChannel+testShutdownHookRegistrationClosesChannel' test
```

The ten six-case invocations were before adding the two independent terminal methods; their existing test method bodies and the production source are unchanged by that final test-only commit. `final-fixed-*` retains all ten results. The complete transport module command at the exact final head, including both extra terminal methods, is the same command with the entire `-Dtest=...` argument removed. Its result and per-suite totals are full-transport-results.json; complete reports are full-transport-reports/.

run-targeted.py and run-module.py are the exact bounded harnesses used. Copy them into the lane directory beside the clone (not inside the clone); create evidence/ there. Neither is a CI monitor, and no CI run was re-requested. No tests were disabled or loosened. Maven/Surefire's JVM GC startup-output warnings are preserved in the logs/dumpstream files.
