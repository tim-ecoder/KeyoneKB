# Manual

## About KeyoneKb2

Latest version: v2.4-build4.6

> The instructions are slightly outdated and do not account for some adaptations made for the Unihertz Titan device family in versions 2.5+

### Description

A keyboard made by fans of hardware keyboard input, for fans of hardware keyboard input. KeyoneKb2 aims to be **more convenient** than **Blackberry Keyboard**, more **configurable** and **customizable** than **ruKeyboard**. The goal is to make working with text so convenient that you never need to take your hands off the keyboard to touch the screen. To achieve this, various text navigation methods and keyboard gestures are widely used. Considerable attention was given to one-handed operation, which is difficult with the stock keyboard and alternatives like ruKeyboard due to language switching requiring two buttons. The keyboard's software architecture is highly extensible and configurable, and can be adapted for other layouts, devices, and languages. Additionally, the keyboard's behavior is fully customizable through JSON configuration files — users can add/remove/change hotkeys and much more.

### Supported devices:

* Blackberry Key1
* Blackberry Key2
* Unihertz Titan Pocket (without keyboard gestures in text input mode)
* NEW! Unihertz Titan Slim (without keyboard gestures in text input mode)
* Can be adapted without recompilation for any device with a hardware keyboard

### Key features:

* Language icon in the status bar, language icon in the bottom swipe panel;
* Single-button language switching (KEY\_0); (Blackberry Key1-2 only)
* CTRL+ACXVZ (and even CTRL+SHIFT+Z) (also works for non-RST Blackberry Key2: SpeedKey → CTRL; Unihertz Titan: Fn → CTRL);
* Keyboard gestures for scrolling and cursor navigation through text (including up-down gestures and selection with SHIFT);
* Navigation buttons (arrows, pg-up-down, home, end, tab, esc); visible NAV-pad;
* SYM-pad to display symbols from the ALT-2 layout (which is not engraved on keys);
* Many small useful features (ALT+DEL; SHIFT+ENTER; SHIFT+DEL, etc.)
* Search plugins that automatically "click" on search when you start typing, with the ability to add more
* Full customizability of all the above through editing JSON files
* Ability to change/add layouts through editing JSON files
* NEW! Pointer mode for hardcore BlackBerry enthusiasts with nostalgia

## Instructions for KeyoneKB2 v2.1+

### Important notes

* For **KEY1** owners: Wherever Ctrl is mentioned, read RIGHT\_SHIFT;
* (v2.4+) For **Unihertz Titan Pocket and Slim** owners: Wherever Ctrl is mentioned, read FN; Where KEY\_0 is mentioned — this button simply does not exist.
* (v2.3+) For **KEY2 non-RST** owners: Where Ctrl is mentioned, read SPEED\_KEY. The SPEED\_KEY function for launching apps will continue to work (except for letters ACXVZ and KEY\_0);
* _Shift everywhere refers to LEFT\_SHIFT_;

> For the on-screen NAV-pad, SYM-pad, and SWIPE panel to work, the "show virtual keyboard" setting must be enabled

### A. Switching input language (layout)

1. KEY\_0; aka ZERO (single press);
2. Shift+Space; (space while holding Shift).

### B. Text input (specifics)

1. Double characters are implemented. Double-click (double press) deletes the first character and inputs the second (double) character;
2. Shift+Backspace = Delete (deletes the character after the cursor);
3. 2xSpace produces ". " (period space);
4. In gesture modes for input fields, swipe panel, and NAV mode you can **select text by holding Shift**;
5. (v2.2+) Shift+Enter moves the cursor to the beginning of the paragraph (if already at the beginning, moves to the start of the previous paragraph);
6. (v2.2+) ALT+DEL deletes everything up to the previous line break (paragraph);
7. (v2.4+) KEY\_0+DEL deletes a word;
8. (v2.4+) ALT+SPACE inserts \t (tab character) in editing mode
9. (v2.4+) Enter works as a custom button (SEARCH, GO, NEXT, etc.) for cases when the host application offers such options (similar to BB.Keyboard behavior). Now search in Wildberries works properly.

### C. Capital letters

1. The first letter is automatically capitalized at the beginning of a text field, after a period followed by a space, and at the beginning of a new line;

> _To force a lowercase letter, press Shift_;

2. Single Shift press makes one letter uppercase, then lowercase continues;
3. Shift+Letters will be uppercase while Shift is held;
4. 2xShift — enables permanent caps lock mode; to disable caps lock, press Shift once;
5. If Setting\_5 is OFF, holding a letter makes it uppercase. If the letter is double: quick press; press-and-hold;

### D. Entering ALT symbols

1. Alt+Key; symbol/digit from the ALT layout (engraved on the keyboard);
2. Alt; Key; single symbol input, then ALT mode is disabled (returns to letter mode); _or if Setting\_3 is enabled, ALT mode will only turn off after pressing Space;_
3. 2xAlt — enables permanent Alt symbol mode. Disabled by pressing Alt.
4. Holding a letter inputs a symbol from the Alt layout. If Setting\_5 is enabled (if the setting is not enabled, an uppercase letter is input);

### E. Entering symbols from the SYM layout (also known as ALT2 or Alt-Shift)

1. In enabled ALT mode, Shift+Key;
2. Shift+Hold;
3. (v.2.2+) In ALT mode (2xALT or held ALT), press and hold;
4. (v.2.2+) In ALT mode, Quick press; press-and-hold; gives the first symbol from the additional symbols list (item 8) (dash on minus, ruble on dollar)
5. Quick press; press-and-hold; (**for one-handed operation**)

### E. On-screen SYM keyboard

6. Pressing Sym opens/closes the on-screen SYM keyboard in Alt2 (sym) layout mode;
7. To switch the on-screen SYM keyboard to ALT mode (and back), press Alt or Shift;
8. Some on-screen symbols support long press, showing additional symbol choices. _For example, the minus symbol "-" has a "—" (em dash) variant_;

### F. Navigation keyboard (NAV keyboard) and NAV mode

1. Allows using Up/Down/Left/Right arrows, HOME, END, PAGE\_UP, PAGE\_DOWN, ESC, TAB, and Shift+Tab;
2. Can be used both in input mode and in viewing mode for various screen navigation;
3. "Temporary" mode (hold/release mode). Hold SYM+KEY. Releasing SYM disables the mode;
4. "Permanent" mode (in text input mode opens the NAV panel. You can tap on it, or look at it and memorize the keyboard letters for use in hold mode). Enable: 2xSYM. Disable: Single SYM press;
5. There are NAV keyboard variants for the right finger (QYUIO/AHJKL) for permanent mode and for the left finger (QWERT/ASDFG) for convenient use while holding SYM.

### G. Keyboard gesture modes (keyboard sensor)

#### Input field gestures (**Cursor mode**)

1. "Temporary" mode. Hold KEY\_0 (ZERO) + gesture on keyboard left-right (if vertical gesture mode is enabled (see below) then also up-down).

> **(<=v2.2)** Due to BlackBerry OS implementation specifics. _Before holding Zero, first place your right finger on the keyboard (you can start making the gesture)_. This is still quite convenient once you get used to it, for example to go back a few characters, make a correction, then return to where you were typing;

> **(v2.3+)** Now to move the cursor while holding KEY\_0, you don't need to place your finger on the keyboard first — you can hold KEY\_0 first, then start making the gesture;

2. "Permanent" gesture mode. Enable: (2.4+) 2xKEY\_0 (<=2.3 2xCtrl). Disable: (2.4+) 1xKEY\_0 (<=2.3 1xCtrl). Keyboard gestures left-right will work until text input.
3. Gesture mode +up-down (for large texts). Enable: (2.4+) 3xKEY\_0 (<=2.3 3xCtrl); Disable: (2.4+) 1xKEY\_0 (<=2.3 1xCtrl). Automatically disables after the first character input.

> Important: (2.4+) 3xKEY\_0 (<=2.3 3xCtrl) enables vertical gesture mode, after which this mode will also work with (2.4+) 2xKEY\_0 (<=2.3 2xCtrl). Until explicitly disabled with (2.4+) 1xKEY\_0 (<=2.3 1xCtrl);

> For Unihertz **Titan Pocket/Slim**, 2xFn is used to activate text gesture mode (the mechanism is "built into" the OS)

> (<=v2.3) **Hint** _To scroll Telegram with gestures, you need to move the cursor out of the input field with the up arrow (sym+u/e)._ **(v2.4)** Scroll mode in editing mode has been added (see below)

5. (v2.2+) Double tap on the keyboard (without releasing the second tap — you can immediately start making the gesture) activates horizontal gesture mode. Releasing deactivates text field gesture mode.
6. (v2.2+) Triple tap on the keyboard (without releasing the third tap — you can immediately start making the gesture) activates horizontal and vertical gesture mode. Releasing deactivates text field gesture mode.

> Important: Triple tap enables vertical gesture mode, after which this mode will also work with double tap

7. (v2.3+) In gesture mode (including vertical) for input fields, you can hold SHIFT to select text

#### Scroll mode in editing mode

While in a messenger dialog (e.g., Telegram), you can press 2xCTRL to enter scroll mode for browsing history using keyboard gestures. Typing any character disables this mode (like in bb.kbd). Previously, you had to "knock" the cursor out of the input field through NAV mode (SYM+A);

#### Gestures in viewing mode

1. If the corresponding setting is enabled (item 9), viewing gestures will work in all applications (except input fields). Enable/Disable: 3xCTRL
2. Scroll mode smoothly scrolls pages using keyboard gestures

> (v2.4+) Everything below

3. 2nd mode — Pointer mode allows moving selection across different interface elements, buttons, etc. (like the old non-touch BlackBerry)

> Interface element selection can also be moved through NAV mode

4. You can switch modes (if enabled) with 2xCTRL
5. The current gesture mode choice for an application switched via 2xCTRL is remembered per application
6. Setting item 9 allows choosing which viewing gesture mode works by default
7. Important: Setting item 9 does not override already saved gesture mode settings for applications

> Sometimes it's unclear where the selected element is — move the pointer with the sensor to find it

8. For easier "exploration" of where the pointer works well and where it doesn't, there is Setting item 13. This setting additionally highlights the selected element with a frame and sets "focus" if the host application hasn't set focus itself
9. In pointer mode, you can click the selected element with ENTER (standard behavior — short press is implemented by the host application), or press SPACE to simulate a CLICK (finger tap). Different applications respond differently
10. Alt+Space performs a long press in pointer mode

### H. Ctrl operations:

1. Text operations: Ctrl+C (Copy); Ctrl+V (Paste); Ctrl+X (Cut); Ctrl+A (Select all);
2. Ctrl+Z (Undo); Ctrl+Shift+Z (Redo, i.e., revert if you accidentally pressed Ctrl+Z).

### I. Other

#### (v2.3+) "Transparency" mode

> In viewing mode (not in text input mode), the keyboard does not process letter presses (except some meta buttons) and sends them to applications as-is. This is important for some applications. For example, games where holding buttons should produce repeated inputs (as games need, for example, to run forward without pressing a button 500 times).

> So that this mode "doesn't interfere" for users who are used to typing letters to open Search, the search plugin mechanism is provided (see below).

#### Swipe panel

* You can make "swipes" on this panel to move the cursor
* You can tap the arrows on the left and right to move the cursor "one by one".

1. The swipe panel is enabled from the settings menu SETTING\_8 (after switching, the keyboard needs to be "restarted" with alt+enter);
2. Shift+Ctrl activates the bottom swipe panel with the flag.

#### Search plugins

> A search plugin is when entering an application and starting to type on the hardware keyboard, the "Search" icon is pressed automatically

2. In **Blackberry Contacts and Phone (Dialer)** applications, you can immediately start typing in Cyrillic after entering, without pressing the "magnifying glass", and search will work.
3. (v2.2+) In **Telegram**, if you start typing in the main window, it will search through chats.
4. (v2.3+) You can add search plugins quite easily ([dedicated section](https://github.com/tim-ecoder/KeyoneKB/wiki/%D0%A1%D0%BB%D0%BE%D0%B6%D0%BD%D1%8B%D0%B5-%D0%BD%D0%B0%D1%81%D1%82%D1%80%D0%BE%D0%B9%D0%BA%D0%B8)), for example a plugin for ExDialer has already been added
5. (v2.3+) Built-in plugins have been expanded with: Yandex (Maps, Navigator), Blackberry (Settings, HUB, Notes, Calendar)
6. (v2.4+) Search plugins now activate on CTRL+ACXV and on DEL and SPACE. For example, you copy an address, open Yandex.Maps, and without pressing search, press ctrl+v: search will open automatically and the address from the clipboard will be entered.

#### (v2.4+) ENSURE\_ENTERED\_TEXT

In text input mode, to exit the application (BACK, HOME), if some text has been entered, you need to press twice (BACK or HOME). If no text is entered or you're not in an input field, a single press is sufficient as usual. This is important because sometimes already entered text is lost due to an accidental finger slip upward (this is important in browser input fields where back resets the input form). Setting item 12 to disable this mode.

### J. Working with calls

#### Setting\_6 must be enabled

1. (v2.2+) Answering a call

* Useful for K1 (K2 has a similar built-in function)
* When a call is incoming
* If not in a text input field
* Then 2xSpace will answer the call

2. (v2.2+) Ending a call

* During a call or incoming call
* If not in an input field
* **Double** pressing SYM will end the call

### (v2.3+) Advanced settings and customizations

In a separate article [Advanced Settings](https://github.com/tim-ecoder/KeyoneKB/wiki/%D0%A1%D0%BB%D0%BE%D0%B6%D0%BD%D1%8B%D0%B5-%D0%BD%D0%B0%D1%81%D1%82%D1%80%D0%BE%D0%B9%D0%BA%D0%B8)

### ACKNOWLEDGMENTS

* Founder: Artem Tverdokhleb aka l3n1n-ua (UA)
* Co-author: krab-ubica (RUS)
* Co-author: Henry2005 aka CapitanNemo (BY)
