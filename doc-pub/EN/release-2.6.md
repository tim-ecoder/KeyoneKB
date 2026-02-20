# Release 2.6

## Release 2.6 (feature freeze)

### Release description

Release 2.6 (final) is dedicated to:

* Improving user experience for people with slow fingers or stiff buttons on Unihertz devices
* Deepening capabilities for experienced keyboard text editing users
* Added voice input functionality
* Some additional conveniences for blind and visually impaired users
* Customization option: pressing 2 adjacent buttons simultaneously
* Improved ease of self-customization of JSON by users

### Improvements/enhancements

**FI4:** Core key press handler update. Now meta buttons alt, shift, sym, key0, ctrl on single press trigger like all others at the moment of pressing down. Previously, specifically these buttons caused frustration for people with slow fingers because they triggered on key release (in particular, language switching). Changing this logic required changes to mechanics and the addition of the undo-short-press block — a command to cancel the single press action. REQUIRES reworking old mechanics customizations based on the new mechanics from default. If you customized core, you can try without it;

**FI4:** Voice input VoiceInput (with click-return to the text field after exiting voice input). Key1|2: ctrl+sym and sym+ctrl; Slim|Pocket: fn+alt and alt+fn. These adjacent buttons can be pressed simultaneously without worrying about which one is pressed first. This opens new customization possibilities when there aren't enough buttons, for example on Unihertz Titan;

**FI4:** Word navigation in NAV mode: SYM+Z SYM+X (and also SYM+N SYM+M) with support for word selection while holding SHIFT;

**FI4:** Normally, when moving an existing selection in NAV mode with SHIFT, the right handle moves (for BlackBerry). To move the left handle, press 2xSHIFT without releasing the second press — then the left handle will move both by words and with all navigation buttons. For Unihertz, it works differently: with 1xSHIFT, the handle that "moved" first always moves; to move the "second" handle that was stationary, press 2xSHIFT without releasing. The base mechanics were changed. For this to work, you need to reapply your customizations.

> There is a peculiarity with the so-called reverse selection on Key2: if you don't wait for the handles to flip when pressing 2xSHIFT and start pressing buttons, the selection will be reset. This is not my selection implementation — unfortunately, this is a quirk of the Canadian customization. The selection mechanism with SHIFT by words was written by me and doesn't have this quirk.

**FI4:** Added ability to delete a word (KEY1|2: key0+shift+del) and paragraph (ALL: alt+shift+del) "forward"

**FI4:** KEY1|2 Added text scroll in input mode before you start pressing anything:

1. It will only activate when some scroll-by-viewing mode is active in the application itself (either scrolling or pointer); To disable this, turn off the sensor for the application with 3xCTRL (and setting item 9 if for all);
2. Or if item 1 is disabled (or scroll was turned off due to button presses), it activates with 2xCTRL. This will work on Unihertz too, but not on the stock OS — only on later OS versions without Chinese kernel modifications

**FI4:** KEY1|2 Enabled gestures in input mode for cursor movement by double tap (now with releasing the second tap), like in bb.keyboard. Previously, it only worked without releasing the second tap;

**FI4:** Added file access permission check in the application window, so that during a clean reinstall, customizations remaining in the application folder are picked up

**FI4:** Added JSON loading ERROR display in the "check operation" form

### Fixed bugs

**BUGFIX:** Fixed loading of dynamic shortcuts (quick settings and notifications invocation) for opening quick settings and notifications, so they can be mapped to hotkeys in OS settings;

**BUGFIX:** Slightly changed search\_plugins behavior for bb.contacts, bb.dialer;

**BUGFIX:** TITAN S|P: Attempted to fix accessibility service activation, which sometimes didn't start immediately;

### JSON customizations (changes to JSON embedded in resources)

**SEARCH\_PLUGIN:** +Shelter, change: yandex.navi

**CLICKER\_PLUGINS:** Reminder — clicker\_plugin clicks on text input when entering a dialog and starting to type; ru.fourpda.client, +avito, +kate

**HACK:** For the native Telegram client, when exiting a dialog (to the dialog list), it wasn't closing text input and the keyboard thought it was still in text mode, so the search plugin wasn't working. This hack, when exiting a dialog by pressing back, sends a TAB code to Telegram and knocks the cursor out of the text field. After this, the search plugin starts working properly after exiting a dialog;

### Included from mini-release v2.6-build0.2:

**JSON:SLIM:POCKET** Added russian-kika layout

**BUGFIX:** With the setting to disable alt with space (in enabled state), when switching between applications, alt mode is now disabled (previously it was not);

**BUGFIX:** In caps-mode, sym symbols were entered instead of expected alt symbols

### Included from mini-release v2.6-build0.3:

Micro-release for BlackBerry Key2 owners

This fixes (mostly) the so-called keyboard lag issue, specifically when the phone is under load/lagging, the keyboard would sometimes (more often for some) input symbols or uppercase letters.

Additionally, to work around the lag (if it still interferes), you can increase the value of the hold event timing. keyboard-core.json -> "time-long-press"

### Included from mini-release v2.6-build0.6:

**BUGFIX:ALL:** Pressing ENTER was sending both a custom action and ENTER (manifested as parasitic clicks in Chrome during search)

**BUGFIX:SLIM:POCKET** SYM-pad was not opening on translit layouts

**JSON:SLIM** ALT layout supplemented with new symbols by NokiaC6-01
