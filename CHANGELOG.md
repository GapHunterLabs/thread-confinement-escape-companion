<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Thread-Confinement Escape Companion Changelog

## [Unreleased]

### Added

- A description page for the inspection in **Settings | Editor |
  Inspections**, which showed "Under construction".

### Changed

- The rating prompt's local counter keeps one-way fingerprints of findings
  instead of their file paths, and deletes the list that earlier versions
  kept.
- `PRIVACY.md` describes the values the plugin keeps in the IDE's local
  settings.

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
