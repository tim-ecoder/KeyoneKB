# Release 2.7

## Release 2.7

**Type:** New version\
**Version:** v2.7b46R\
**Summary:** Stable release dated 21.06.2025 by krab-ubica

### Release Description

Release 2.7 is dedicated to:

* [Unihertz Titan support](https://4pda.to/forum/index.php?showtopic=958963\&view=findpost\&p=136872780) (extended)
* Bug fixes and code refactoring
* Usability improvements for some features
* Major deepening of JSON customizability

### Improvements / Enhancements

* **REF** Light refactoring of on-screen keyboards
* Pop-up extra characters moved to the top for UHZ S|P (Unihertz Slim/Pocket)
* Reworked swipe panel height setting
* Swipe panel buttons now occupy full size
* Flag (language indicator) is now positioned relative to the layout name
* Arrows are now centered
* **FI4** Ctrl+Alt — Paste clipboard contents character-by-character to bypass anti-paste scripts on websites
* **FI4** Word-by-word navigation now automatically skips punctuation
* **FI4** Improved clicker plugins for modded/cloned apps that change a single letter in the package name; improved Telegram hack for Telegram clones
* **FI4** Long-press on the on-screen panel (NAV and swipe) now repeats input
* **\[BB K1|2]** **FI4** Added 0 (zero) to the on-screen SYM keyboard
* **FI4** Enabled hardware DEL key functionality in NAV mode
* **\[UHZ T]** Added a workaround for Unihertz so that Ctrl+C works when selection was extended leftward to the very beginning

### Bug Fixes

* **BUG-FIX** Fixed swipe panel behavior:
  * **\[UHZ S|P]** Swipe panel now opens when entering edit mode without requiring a tap on the input field
  * **\[BB K1|2]** As it turned out, it was working erratically — sometimes it wouldn't hide, sometimes it wouldn't appear
* **BUG-FIX** Previous toast notification about language change (if still visible) is now replaced by a new one. This is important for integration with TLT (Text Layout Tools)
* **BUG-FIX** The key-down array for single-press was not being cleared
* **BUG-FIX** Auto-capitalization was not applied when moving the cursor via SYM hold
* **BUG-FIX** Letter was not capitalized after Alt+Del
* **\[UHZ T]** Fixed parasitic "v" and "x" characters being inserted in Telegram when using Ctrl+V or Ctrl+X
* **\[UHZ ?]** Fixed settings being reset unexpectedly

### Customizations

* **FI4** Reworked NAV mechanism and moved it to JSON
* Added Unihertz Titan support and corresponding layouts (together with zynaps, we even figured out how to implement both Ctrl and NAV mode)
* Added Latvian/Estonian layouts
* **FI4** JavaScript engine for granular patching of JSON files
* **FI4** [JS patches](https://4pda.to/forum/index.php?showtopic=1024645\&view=findpost\&p=137722519) can now be enabled/disabled via the Advanced Settings menu
  * Useful so you don't have to re-apply your custom changes every time the developer updates the mechanics file
  * Allows you to try out convenience changes made by other users for themselves
* **REF** Restructured JSON file storage in the app source code (files moved to Assets and organized into folders)
* **FI4** In mechanics, you can now customize individual keys (one from a group)
  * In memory, each key's settings from a group are duplicated/copied per key, so changes will only apply to the specific key where the mechanics section or JS patch is applied
  * This greatly expands customization capabilities for cases such as when a specific key is broken

### Device Tags Legend

| Tag         | Device                       |
| ----------- | ---------------------------- |
| \[BB K1\|2] | BlackBerry Key1 / Key2       |
| \[UHZ T]    | Unihertz Titan               |
| \[UHZ S\|P] | Unihertz Titan Slim / Pocket |
| \[UHZ ?]    | Unihertz (ALL)               |

### Version Tags Legend

| Tag     | Meaning                                  |
| ------- | ---------------------------------------- |
| FI4     | Feature / Improvement (internal tracker) |
| REF     | Refactoring                              |
| BUG-FIX | Bug fix                                  |
