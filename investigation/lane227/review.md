# Lane 227 separate review passes

## Test review (2026-10-15 12:46 UTC)
- Re-read the three original PR regression cases and the new class separately from production. Original cases are unchanged.
- New close-listener chain uses eight real OS sockets and actual public event-loop/channel APIs. Initial registration success and all completed registration futures are checked; physical closure is asserted before deferred cleanup assertions so the demonstrated open-channel defect cannot be hidden behind an earlier deregistration failure.
- New channelUnregistered callback chain independently makes final deregistration observable; it fails baseline at the registered flag after termination.
- Both new failure tests fail deterministically 3/3 against unchanged d7b4547b, not at test budgets. Existing finite callback ordering creates the condition directly, with no scheduler load, mocks, private state, reflection, new timeout, sleep or retry.
- Live-loop close listener registration is explicitly tested to keep successful registration/open state until caller close; passes baseline and fixed runs.
- Raw sockets are test-owned and all close in finally, even for failing baseline assertions. Event-loop termination waits are bounded. Lists are written on the single loop and read after the termination-future completion.
- No bound/iteration/assertion in an existing test changed. Tests exercise ownership only through public APIs.

## Code review (2026-10-15 12:46 UTC)
- Traced SingleThreadEventExecutor.run/finally, confirmShutdown, execute rejection, and AbstractChannel.register0/close/deregister plus AbstractNioChannel.doRegister/doDeregister.
- Terminal admission is event-loop-thread confined. An external registration is queued, then checked at actual IO registration; reentrant same-thread registration takes the same guard. isShutdown also rejects an immediate shutdown already beyond graceful admission.
- Graceful admission is retained until superclass confirmation returns true, so shutdown-hook registration remains supported. Before returning true, the new final snapshot is closed with admission closed and deferred deregistration is drained while graceful cleanup can still enqueue work.
- Subsequent superclass final confirmation passes cannot admit new IO, including tasks queued just before ST_SHUTDOWN. No number-of-registrations drain loop can be extended by additional successful callback registrations.
- Suspension never closes admission because confirmation returns false before any action on a live loop. Terminal flag is never reset; no persisted restart state exists.
- Existing IoHandler interface already permits repeated prepareToDestroy. Enumerated NIO, local, epoll, kqueue, io_uring and test fixtures; no new method signature/call kind is introduced. Native execution suites are not part of this lane's requested transport-suite validation.
- README is an upstream overview and this change adds no public API/setup/dependency change; no README edit is needed in the development fork. Release README/version belong to the separate release PR required before publishing.

No blocking finding in the scoped NIO callback shutdown fix at this review checkpoint. Final module run and exact-head rebase/test remain required before the PR branch is pushed.

## Final boundary review (2026-10-15 12:53 UTC)
The final five-test class also ran against the old production class. Both original new callback assertions still fail, so stronger failure-message checks did not remove the original signal. Immediate same-thread registration after deprecated shutdown() additionally fails for a successful registration on old source; external caller registration after actual termination already rejects correctly. The latter is explicitly preservation coverage, not a claimed new defect. The new isShutdown guard covers the demonstrated immediate-shutdown path. No additional production edit was needed. All three original PR tests passed in every one of the final ten six-case JVMs.

## Final module gate (2026-10-15 12:57 UTC)
At exact final head 7a9caa8cfd38683230db50ae83c58f22002044ba, the full transport module passes 427 discovered cases with 0 failures/errors and 9 existing skips. NioEventLoopTest has 16 passing cases; NioShutdownReentryTest has 5 passing cases; both have zero skips. All original test source is unchanged. Immediate-shutdown and post-termination assertions pass on the corrected source. No source change followed this full-suite result. Both review passes are complete; no blocking finding in the scoped correction.
