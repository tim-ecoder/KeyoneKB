# Release 2.5

## Release 2.5 (released)

### Release description

Release 2.5 is dedicated to:

* Better quality support for Unihertz Titan Slim|Pocket devices;
* Comprehensive bug fixing (including BB Key1|2);
* Improving extensibility/customizability for new layouts and devices;
* Ensuring minimum necessary readiness for public release (relative to the Russian-speaking internet).

### Improvements/enhancements

* Fi4:BUGFIX: SYM-pad adaptation for Pocket;
* FI4: Added shortcuts to open the notification shade and quick settings shade, so they can be assigned to a speed-key;
* FI4: Saving and reading files from the more convenient /storage/emulated/0/KeyoneKb2 folder;

> \[Attention!] If you have custom JSON files, they need to be moved to the new folder;

* FI4: Added device model filter (regexp) in layout settings to hide layouts from other devices;

> \[Attention!] If you have _additional_ custom JSON layouts registered in keyboard\_layouts, you need to add "device-model-regexp" : "Blackberry (BBB|BBF|BBE).\*" or "device-model-regexp" : "Unihertz Titan (Slim|Pocket)" — check the default/ folder

* Fi4: Made the main menu scrollable (didn't fit on Pocket);
* Fi4: Added device model indicator in settings, buttons to restart the keyboard and accessibility service;
* Fi4: Disabled aggressive Pointer mode focus with activated Frame (it was setting focus on an element by itself, which was more annoying than helpful);
* Fi4: For easier customization, added the ability to use KeyEvent constants of the form KEYCODE\_X as keycode in JSON (backward compatibility for numeric values is preserved) \[all];
* FI4: Flag (resource reference) on the swipe panel moved to keyboard\_layouts.json;
* FI4: Added ClickerPlugins array which works exactly like SearchPlugins — you can now click two different fields in one application depending on the situation;
* FI4: Reduced text size in layouts so longer names fit on a single line, and also reduced size in settings for a more pleasant look;
* FI4: Setting item 14 to not show NAV\_pad on single (hold) SYM press
* Fi4: English translation

### JSON customizations (changes to JSON embedded in resources)

* JSON:All 123 mode. For those who use True Phone, in the keyonekb2\_as\_options.json file;
* JSON:Pocket:Slim updated layout with the letter "ё" on double press "й"/"q";
* Json:Pocket:Slim: 1xSym switches layout, Fn+Sym opens Sym-pad (1xSym closes, 2xSym also closes);
* Json:Pocket:Slim: 2xFn (out of the box moves cursor through text) added corresponding indicator in the notification panel;
* JSON:Pocket:Slim: Added Translit layout;
* JSON:Pocket:Slim: Answer call with SHIFT, end call with ALT;
* JSON:Pocket:Slim: Merged Pocket/Slim layouts;
* JSON:Pocket:Slim: Added layout close to bb.key2;
* JSON:Pocket:Slim: Bugfix: Wrong alt layout for Translit;
* JSON:Pocket:Slim: Cleaner version of translit and fixed binding to the correct alt layout;
* JSON:SearchPlugin:ClickPlugin: ClickPlugin added com.google.android.dialer to click on the number pad (for Slim:Pocket) and SearchPlugin to click on contact search;

### Fixed bugs

* BUGFIX: Fixed Ctrl sticking for BB K2 non-RST;
* BUGFIX: Accessibility service was not starting on its own without the keyboard, and because of this on Pocket/Slim it was not starting automatically on reboot;
* BUGFIX: Fixed TAB, ESC, HOME, END presses on the on-screen SYM keyboard (with SYM held);
* BUGFIX: ALT2 symbol on press/press-and-hold was deleting extra characters for double letters;
* BUGFIX: Removed offset for Titan Slim/Pocket element selection frame for Pointer mode;
* BUGFIX: For unknown devices (e.g., the large Titan, which is not officially supported yet), at least one keyboard should be shown in settings;
* BUGFIX:SLIM:POCKET Crashed on phone startup (SharedPreferences in credential encrypted storage are not available until after user is unlocked);
* BUGFIX: Cleaned up nav-pad and swipe-panel appearance with scaling for Slim|Pocket;
* BUGFIX: Crashed on LongPress on the on-screen NAV panel.

### Known bugs

* \[ALL] (Often) Firefox on some sites (e.g., cdek.ru shipping cost calculator) does not activate the keyboard in input fields. This is unfixable as it's a Firefox bug. Other browsers work fine;
* \[Titan|Pocket] (Sometimes) When activating a search plugin in some applications (Island, Avito), the first letter may be doubled (first one will be Latin) — this is an OS behavior. As a workaround, press backspace to activate the search plugin;
* \[Titan|Pocket] (Occurs on only one device) Keyboard crash when pressing Fn+Sym to open the SYM-pad;
* \[BB] Very rarely, when the OS is lagging, instead of Russian letters, a Latin letter or alt symbol is entered. This is most likely unfixable — it's an OS behavior;
* \[Titan|Pocket] (Always) On-screen pads do not open until you tap on an input field. This is an OS behavior.
