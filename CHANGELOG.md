<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Thread-Confinement Escape Companion Changelog

## [Unreleased]

## [0.1.1]

### Fixed

- Review/star CTA now links to this plugin's own Marketplace
  reviews page instead of the vendor's generic plugin list.

## [0.1.0]

### Added

- Real escape analysis into a lambda/anonymous class body (which
  outer-scope variables it captures) combined with a same-method
  post-hand-off access check: flags a mutable local (`List`/`Map`/
  `Set`) shared with a background thread
  (`ExecutorService.submit/execute`, `new Thread(...).start()`) that
  the same method also touches afterward outside any `synchronized`
  block -- shared mutable state with no real protection (CWE-362/366).

[Unreleased]: https://github.com/GapHunterLabs/thread-confinement-escape-companion/compare/0.1.1...HEAD
[0.1.1]: https://github.com/GapHunterLabs/thread-confinement-escape-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/thread-confinement-escape-companion/commits/0.1.0
