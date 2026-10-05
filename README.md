# Thread-Confinement Escape Companion

Flags a mutable local variable shared with a background thread that
the same method also touches afterward without synchronization.

## Why it exists

CWE-362/366. Thread confinement (never sharing mutable state across
threads) is the standard strategy for avoiding race conditions, and
Java has no language-level way to enforce it -- exactly the gap a
static analyzer needs to cover. Real, documented in reference material
(MIT 6.005/6.031 Thread Safety). Searched explicitly for a named
competitor (FindBugs rule, CodeQL query, Datadog rule) for this exact
angle and found none -- stated honestly, unlike this catalog's other
Tier -3 entries where a specific absence was confirmed by name.

## Why built this way

- **Real escape analysis over a boundary never analyzed before in this
  catalog** -- which outer-scope variables a lambda/anonymous class
  body actually CAPTURES. `interprocedural-resource-leak-companion`'s
  own engine deliberately never descends into a lambda/anonymous class
  body when computing a method summary (a separate execution context);
  this plugin does the opposite on purpose, descending specifically to
  see what escapes INTO it.
- **A genuinely new sink domain** -- concurrency/thread hand-off,
  rather than a return value (`mutable-state-leak-companion`) or a SQL/
  log/query string (the taint-to-sink plugins).

## v0.1 scope — stated honestly, not exhaustively

- Only `List`/`Map`/`Set` local variables declared in the SAME method
  (never a captured field via implicit `this`).
- Only an inline lambda or anonymous class passed directly at the call
  site (never a `Runnable` constructed elsewhere and passed in by
  reference).
- The post-hand-off access must be textually in the SAME method.
- A local declared with an interface type (`Map`/`List`/`Set`) but
  initialized directly with a genuinely thread-safe implementation
  (`ConcurrentHashMap`, `CopyOnWriteArrayList`, a
  `Collections.synchronizedXxx(...)` wrapper, ...) is never tracked --
  those types provide real thread safety of their own. Reassigning the
  variable to a thread-safe implementation AFTER its declaration
  (rather than at the initializer) is still tracked -- a known,
  honest v0.1 limitation.

## Usage

Open a Java method that hands a mutable collection to
`ExecutorService.submit(...)`/`new Thread(...).start()` via a captured
lambda/anonymous class, then touches that same collection again
outside a `synchronized` block -- the hand-off call site shows a
warning.

## Support

- **Bugs and feature requests:** [GitHub Issues](https://github.com/GapHunterLabs/thread-confinement-escape-companion/issues)
- **Questions, or custom rules for a team's codebase:** **gaphunterlabs@gmail.com**
- **Security vulnerabilities:** report privately as described in [SECURITY.md](SECURITY.md), not in a public issue.
- **Privacy and network behavior:** [PRIVACY.md](PRIVACY.md)

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
