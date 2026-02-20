# Release 2.4

## Release 2.4 (released)

### Release description

Release 2.5 is dedicated to:

* Better quality support for Unihertz Titan Slim|Pocket devices
* Comprehensive bug fixing (including BB Key1|2)
* Improving extensibility/customizability for new layouts and devices
* Ensuring minimum necessary readiness for public release (relative to the Russian-speaking internet)

### Improvements/enhancements

* Fi4:BUGFIX: SYM-pad adaptation for Pocket
* FI4: Added shortcuts to open the notification shade and quick settings shade
* FI4: Saving and reading files from the /storage/emulated/0/KeyoneKb2 folder
* FI4: Added device model filter (regexp) in layout settings
* Fi4: Made the main menu scrollable (didn't fit on Pocket)
* Fi4: Added device model indicator in settings
* Fi4: Disabled aggressive Pointer mode focus with activated Frame
* Fi4: For easier customization, added the ability to use KeyEvent constants in JSON
* FI4: Flag on the swipe panel moved to keyboard\_layouts.json
* FI4: Added ClickerPlugins array
* FI4: Reduced text size in layouts
* FI4: Setting item 14 to not show NAV\_pad on single SYM press
* Fi4: English translation

### JSON customizations

* JSON:All 123 mode for True Phone users
* JSON:Pocket:Slim updated layout with the letter "ё"
* Json:Pocket:Slim various layouts and functional improvements
* JSON:SearchPlugin:ClickPlugin added com.google.android.dialer

### Fixed bugs

* BUGFIX: Fixed Ctrl sticking for BB K2
* BUGFIX: Accessibility service was not starting on its own
* BUGFIX: Fixed TAB, ESC, HOME, END presses on the on-screen SYM keyboard
* BUGFIX: ALT2 symbol on press was deleting extra characters
* BUGFIX: Removed offset for Titan Slim/Pocket element selection frame
* BUGFIX: For unknown devices, at least one keyboard should be shown
* BUGFIX: Crashed on phone startup (SharedPreferences)
* BUGFIX: Cleaned up the nav-pad and swipe-panel appearance
* BUGFIX: Crashed on LongPress on the on-screen NAV panel

### Known bugs

* \[ALL] Firefox on some sites does not activate the keyboard
* \[Titan|Pocket] When activating a search plugin, the first letter may be doubled
* \[Titan|Pocket] Keyboard crash when pressing Fn+Sym
* \[BB] Rarely, instead of Russian letters, a Latin letter or alt symbol is entered
* \[Titan|Pocket] On-screen pads do not open until you tap on an input field
