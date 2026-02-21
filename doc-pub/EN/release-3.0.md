# Release 3.0

## Release 3.0

Type: Major release\
Version: v3.0\
Summary: Major update — rebranding to K12KB, native prediction and translation engines, emoji keyboard, on-screen keyboards redesign (sym, swipe, nav), UI redesign with light/dark themes

### Release Description

Release 3.0 is dedicated to:

* Rebranding from KeyoneKB to K12KB with new namespace and icon
* Full-featured word prediction engine with native SymSpell (C99 + JNI)
* Offline translation with context-aware phrase matching
* Emoji keyboard support
* On-screen keyboards redesign SYM, SWIPE, NAV
* Complete UI and settings redesign with light/dark themes

### Rebranding

* Changed application namespace to `com.ai10.k12kb`
* Renamed all internal references from keyonekb/keyonekb2 to k12kb
* New deep sea blue K12KB launcher icon
* Version bumped to 3.0

### Word Prediction Engine

* Added word prediction with suggestion bar (based on Pastiera)
* **Native SymSpell engine** written in C99 with JNI bindings for aarch64 — significantly faster than Java implementation
* Dictionaries expanded up to 750k words using wordfreq (OpenSubtitles 2018) source
* **Dictionary size switchers** — choose between tiny, normal, big and full dictionaries
* Binary cache format for fast dictionary loading across IME restarts
* Static engine caches dictionaries across IME restarts to avoid reloading
* Sequential dictionary preloading at startup (EN first, then RU)
* Throttled loading to prevent typing lag at startup
* Toast notification when typing while dictionary is still loading
* Dictionary status and cache management in Suggestions settings screen
* Configurable prediction count (1–6 suggestion slots)
* **Ctrl+W** toggles prediction bar on/off
* Preference to enable/disable word prediction entirely
* Preference to disable autocapitalization
* Preference to disable keyboard-aware corrections
* Word learning from typed text (learns as you type)
* Android UserDictionary integration for prediction

### Offline Translation

* **Offline file-based translation** displayed in prediction pillows
* **Native CDB-based translation engine** for fast dictionary lookups
* Translation dictionaries sourced from OpenRussian word forms + Wiktionary
* Context-aware phrase translation using bigram dictionaries
* Bigram context dictionary for previous-word-aware translation
* Phrase translation replaces both words when accepting a phrase match
* Sort translation dictionaries by target language word frequency
* Separate translation pillows count preference
* Translation dictionary size switchers (tiny, normal, big, full)
* **Ctrl+T** toggles translations on/off

### Emoji Keyboard

* Added emoji keyboard with 5 pages of smileys
* Replaced Emoji 11.0+ with Emoji 5.0 compatible set for Android 8.x support
* Visible EMO label on SYM pad for easy access

### Suggestion Bar

* Restyled suggestion bar to match on-screen keyboard design
* Dynamic slot widths based on text length with proportional word-length weighting
* Priority word displayed in full in center slot, never truncated
* Non-priority words truncate from start (show word endings)
* Empty slots hidden, remaining slots fill available space
* Prediction bar pushes app content up via onComputeInsets
* Bar remains visible when swype-pad is disabled

### UI / Settings Redesign

* **Light theme** with toggle in settings
* GUI redesign with dark pills and compact layout
* Numbers in settings highlighted by circles
* Pill text truncated with ellipsis, click to expand full text
* Settings save/load with structured JSON backup (includes json\_patches and swipe\_modes sections)
* Interface language switcher on main screen
* Collapsible granted permissions group with theme-aware styling
* Main layout rearranged with dynamic element positioning
* Russian translations for settings screens (Appearance, Prediction, Backup)

### JS Patches

* Human-readable names on JS patch pills (from `// @name` comments in files)
* JS patches grouped by resource name with sub-headers
* JS patch pill text click opens file in editor (separate from toggle)
* Russian translations for JS patch editor toast messages
* JS PATCHES section hidden entirely when no patch files found on SD card
* JS patches loaded only from SD card (no auto-deploy from assets)
* **Added JSpatch: Disable chats reading/scrolling** default mode

### Keyboard Shortcuts

| Shortcut       | Action                          |
| -------------- | ------------------------------- |
| Ctrl+W         | Toggle prediction bar on/off    |
| Ctrl+T         | Toggle translations on/off      |
