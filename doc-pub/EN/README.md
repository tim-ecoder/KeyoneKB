# K12KB User Guide

## About K12KB

### Description

A keyboard made by fans of hardware keyboard input, for fans of hardware keyboard input. K12KB aims to be **more convenient** than **Blackberry Keyboard**, more **configurable** and **customizable** than **ruKeyboard**. The goal is to make working with text so convenient that you never need to take your hands off the keyboard to touch the screen. To achieve this, various text navigation methods and keyboard gestures are widely used. Considerable attention was given to one-handed operation, which is difficult with the stock keyboard and alternatives like ruKeyboard due to language switching requiring two buttons. The keyboard's software architecture is highly extensible and configurable, and can be adapted for other layouts, devices, and languages. Additionally, the keyboard's behavior is fully customizable through JSON configuration files -- users can add/remove/change hotkeys and much more.

### Supported devices

| Device | Notes |
|--------|-------|
| Blackberry Key1 | Full support |
| Blackberry Key2 | Full support |
| Unihertz Titan | Full support |
| Unihertz Titan Pocket | Without keyboard gestures in text input mode |
| Unihertz Titan Slim | Without keyboard gestures in text input mode |
| Other | Can be adapted without recompilation for any device with a hardware keyboard |

### Key features

* Language icon in the status bar and in the bottom swipe panel
* Single-button language switching (`KEY_0`) (Blackberry Key1/Key2 only)
* `Ctrl`+`A`/`C`/`X`/`V`/`Z` (and even `Ctrl`+`Shift`+`Z`)
* Keyboard gestures for scrolling and cursor navigation through text (including up-down gestures and selection with `Shift`)
* Navigation buttons (arrows, Page Up/Down, Home, End, Tab, Esc) and visible NAV-pad
* SYM-pad to display symbols from the ALT-2 layout (which is not engraved on keys)
* Many small useful features (`ALT`+`DEL`, `Shift`+`Enter`, `Shift`+`DEL`, etc.)
* Search plugins that automatically "click" on search when you start typing, with the ability to add more
* Full customizability of all the above through editing JSON files
* Ability to change/add layouts through editing JSON files
* Pointer mode for BlackBerry enthusiasts with nostalgia

---

## User Guide

### Important notes

The `Ctrl` key differs by device:

| Device | `Ctrl` key equivalent | Notes |
|--------|----------------------|-------|
| **Key1** | `RIGHT_SHIFT` | Wherever `Ctrl` is mentioned, use `RIGHT_SHIFT` |
| **Key2 (non-RST)** | `SPEED_KEY` | The `SPEED_KEY` app-launching function still works (except for letters A, C, X, V, Z and `KEY_0`) |
| **Unihertz Titan** | `CTRL` | Has a dedicated physical `Ctrl` key |
| **Unihertz Titan Pocket/Slim** | `FN` | `KEY_0` does not exist on these devices |

> `Shift` everywhere refers to `LEFT_SHIFT`.

> For the on-screen NAV-pad, SYM-pad, and SWIPE panel to work, the "show virtual keyboard" setting must be enabled.

### A. Switching input language (layout)

1. `KEY_0` (single press)
2. `Shift`+`Space` (space while holding `Shift`)

### B. Text input (specifics)

1. Double characters are implemented. Double-click (double press) deletes the first character and inputs the second (double) character.
2. `Shift`+`Backspace` = Delete (deletes the character after the cursor).
3. `2xSpace` produces ". " (period followed by space).
4. In gesture modes for input fields, swipe panel, and NAV mode you can **select text by holding `Shift`**.
5. `Shift`+`Enter` moves the cursor to the beginning of the paragraph (if already at the beginning, moves to the start of the previous paragraph).
6. `ALT`+`DEL` deletes everything up to the previous line break (paragraph). `ALT`+`Shift`+`DEL` deletes the paragraph **forward**.
7. `KEY_0`+`DEL` deletes a word. `KEY_0`+`Shift`+`DEL` deletes a word **forward** (Key1|2 only).
8. `ALT`+`Space` inserts a tab character (\t) in editing mode.
9. `Enter` works as a custom button (SEARCH, GO, NEXT, etc.) for cases when the host application offers such options (similar to BB Keyboard behavior).

### C. Capital letters

1. The first letter is automatically capitalized at the beginning of a text field, after a period followed by a space, and at the beginning of a new line.

> To force a lowercase letter, press `Shift`.

2. Single `Shift` press makes one letter uppercase, then lowercase continues.
3. `Shift`+Letters will be uppercase while `Shift` is held.
4. `2xShift` enables permanent caps lock mode. To disable caps lock, press `Shift` once.
5. If Setting 5 is OFF, holding a letter makes it uppercase. If the letter is double: quick press, then press-and-hold.

### D. Entering ALT symbols

1. `Alt`+Key: symbol/digit from the ALT layout (engraved on the keyboard).
2. `Alt`, then Key: single symbol input, then ALT mode is disabled (returns to letter mode). If Setting 3 is enabled, ALT mode will only turn off after pressing `Space`.
3. `2xAlt` enables permanent Alt symbol mode. Disabled by pressing `Alt`.
4. Holding a letter inputs a symbol from the Alt layout if Setting 5 is enabled (if the setting is not enabled, an uppercase letter is input instead).

### E. SYM symbols and on-screen SYM keyboard

#### Entering SYM symbols

SYM symbols come from the SYM layout (also known as ALT2 or Alt-Shift).

1. In enabled ALT mode, `Shift`+Key.
2. `Shift`+Hold.
3. In ALT mode (`2xALT` or held `ALT`), press and hold.
4. In ALT mode, quick press then press-and-hold gives the first symbol from the additional symbols list (item 8 below) -- for example, dash on minus, ruble on dollar.
5. Quick press then press-and-hold (**for one-handed operation**).

#### On-screen SYM keyboard

6. Pressing `SYM` opens/closes the on-screen SYM keyboard in ALT2 (SYM) layout mode.
7. To switch the on-screen SYM keyboard to ALT mode (and back), press `Alt` or `Shift`.
8. Some on-screen symbols support long press, showing additional symbol choices. For example, the minus symbol "-" has an em dash variant.

### F. Navigation keyboard (NAV keyboard) and NAV mode

1. Allows using Up/Down/Left/Right arrows, `HOME`, `END`, `PAGE_UP`, `PAGE_DOWN`, `ESC`, `TAB`, `Shift`+`Tab`, and `DEL`.
2. Can be used both in input mode and in viewing mode for various screen navigation.
3. "Temporary" mode (hold/release mode): Hold `SYM`+Key. Releasing `SYM` disables the mode.
4. "Permanent" mode (in text input mode opens the NAV panel -- you can tap on it, or look at it and memorize the keyboard letters for use in hold mode). Enable: `2xSYM`. Disable: single `SYM` press.
5. There are NAV keyboard variants for the right finger (`Q`/`Y`/`U`/`I`/`O` and `A`/`H`/`J`/`K`/`L`) for permanent mode, and for the left finger (`Q`/`W`/`E`/`R`/`T` and `A`/`S`/`D`/`F`/`G`) for convenient use while holding `SYM`.
6. Word-by-word navigation: `SYM`+`Z`/`N` (previous word) and `SYM`+`X`/`M` (next word). Punctuation is skipped automatically. Word selection is supported while holding `Shift`.
7. To switch the active selection handle: press `2xShift` (without releasing the second press), then the opposite handle will move with all navigation keys and word navigation.

### G. Keyboard gesture modes (keyboard sensor)

#### Input field gestures (Cursor mode)

1. "Temporary" mode: Hold `KEY_0` + gesture on keyboard left-right (if vertical gesture mode is enabled, then also up-down).

> To move the cursor while holding `KEY_0`, you don't need to place your finger on the keyboard first -- hold `KEY_0` first, then start the gesture.

2. "Permanent" gesture mode. Enable: `2xKEY_0`. Disable: `1xKEY_0`. Keyboard gestures left-right will work until text input.
3. Gesture mode + up-down (for large texts). Enable: `3xKEY_0`. Disable: `1xKEY_0`. Automatically disables after the first character input.

> Important: `3xKEY_0` enables vertical gesture mode, after which this mode will also work with `2xKEY_0`. It remains active until explicitly disabled with `1xKEY_0`.

> For Unihertz **Titan Pocket/Slim**, `2xFn` is used to activate text gesture mode (the mechanism is "built into" the OS).

> **Hint:** To scroll in Telegram, use Scroll mode in editing mode (see below), or move the cursor out of the input field using the up arrow (`SYM`+`U`/`E`).

4. Double tap on the keyboard activates horizontal gesture mode. Works both with and without releasing the second tap. Releasing deactivates text field gesture mode.
5. Triple tap on the keyboard activates horizontal and vertical gesture mode. Works both with and without releasing the third tap. Releasing deactivates text field gesture mode.

> Important: Triple tap enables vertical gesture mode, after which this mode will also work with double tap.

6. In gesture mode (including vertical) for input fields, you can hold `Shift` to select text.

#### Scroll mode in editing mode

While in a messenger dialog (e.g., Telegram), you can press `2xCtrl` to enter scroll mode for browsing history using keyboard gestures. Typing any character disables this mode.

If viewing gestures are enabled for the application (Setting 9 or `3xCtrl`), scroll mode activates automatically when entering an input field, before you start pressing anything. To disable this, turn off the sensor for the application with `3xCtrl`.

#### Gestures in viewing mode

1. If the corresponding setting is enabled (Setting 9), viewing gestures will work in all applications (except input fields). Enable/Disable: `3xCtrl`.
2. Scroll mode smoothly scrolls pages using keyboard gestures.
3. Pointer mode allows moving selection across different interface elements, buttons, etc. (like the old non-touch BlackBerry).

> Interface element selection can also be moved through NAV mode.

4. You can switch modes (if enabled) with `2xCtrl`.
5. The current gesture mode choice for an application switched via `2xCtrl` is remembered per application.
6. Setting 9 allows choosing which viewing gesture mode works by default.
7. Setting 9 does not override already saved gesture mode settings for applications.

> Sometimes it is unclear where the selected element is -- move the pointer with the sensor to find it.

8. For easier exploration of where the pointer works well and where it does not, there is Setting 13. This setting additionally highlights the selected element with a frame and sets "focus" if the host application has not set focus itself.
9. In pointer mode, you can click the selected element with `Enter` (standard behavior -- short press is implemented by the host application), or press `Space` to simulate a click (finger tap). Different applications respond differently.
10. `Alt`+`Space` performs a long press in pointer mode.

### H. Ctrl operations

1. Text operations: `Ctrl`+`C` (Copy), `Ctrl`+`V` (Paste), `Ctrl`+`X` (Cut), `Ctrl`+`A` (Select all).
2. `Ctrl`+`Z` (Undo), `Ctrl`+`Shift`+`Z` (Redo, i.e., revert if you accidentally pressed `Ctrl`+`Z`).
3. `Ctrl`+`Alt` — paste clipboard contents character-by-character. Useful for bypassing anti-paste scripts on websites.

### I. Voice input

Activate voice input with a simultaneous press of two adjacent buttons (order does not matter):

| Device | Shortcut |
|--------|----------|
| **Key1/Key2** | `Ctrl`+`Sym` or `Sym`+`Ctrl` |
| **Titan Pocket/Slim** | `Fn`+`Alt` or `Alt`+`Fn` |

After exiting voice input, the cursor automatically returns to the text field.

### J. Other

#### "Transparency" mode

In viewing mode (not in text input mode), the keyboard does not process letter presses (except some meta buttons) and sends them to applications as-is. This is important for some applications. For example, games where holding buttons should produce repeated inputs (as games need, for example, to run forward without pressing a button 500 times).

So that this mode does not interfere for users who are used to typing letters to open Search, the search plugin mechanism is provided (see below).

#### Swipe panel

* You can make "swipes" on this panel to move the cursor.
* You can tap the arrows on the left and right to move the cursor one step at a time.
* Long-press on the on-screen NAV-pad or swipe panel buttons repeats the action.

1. The swipe panel is enabled from the settings menu (Setting 8). After switching, the keyboard needs to be restarted with `Alt`+`Enter`.
2. `Shift`+`Ctrl` activates the bottom swipe panel with the flag.
3. Setting 14 controls whether the NAV-pad is shown on a single `SYM` press (hold).

#### Search plugins

A search plugin is a feature where, upon entering an application and starting to type on the hardware keyboard, the "Search" icon is pressed automatically.

1. In **Blackberry Contacts and Phone (Dialer)** applications, you can immediately start typing in Cyrillic after entering, without pressing the "magnifying glass", and search will work.
2. In **Telegram**, if you start typing in the main window, it will search through chats.
3. You can add search plugins quite easily ([dedicated section](advanced.md)). For example, a plugin for ExDialer has already been added.
4. Built-in plugins have been expanded with: Yandex (Maps, Navigator), Blackberry (Settings, HUB, Notes, Calendar).
5. Search plugins also activate on `Ctrl`+`A`/`C`/`X`/`V`, on `DEL`, and on `Space`. For example, you copy an address, open Yandex Maps, and without pressing search, press `Ctrl`+`V`: search will open automatically and the address from the clipboard will be entered.
6. **Clicker plugins** work similarly to search plugins but click on text input fields instead of search. For example, when entering a messenger dialog and starting to type, the clicker plugin automatically focuses the message input field. Built-in clicker plugins include 4PDA, Avito, Kate, and others.

#### ENSURE_ENTERED_TEXT

In text input mode, to exit the application (`BACK`, `HOME`), if some text has been entered, you need to press twice (`BACK` or `HOME`). If no text is entered or you are not in an input field, a single press is sufficient as usual. This is important because sometimes already entered text is lost due to an accidental finger slip upward (this is important in browser input fields where back resets the input form). Use Setting 12 to disable this mode.

### K. Working with calls

> Setting 6 must be enabled for call features.

1. **Answering a call**
   * Useful for Key1 (Key2 has a similar built-in function)
   * When a call is incoming and you are not in a text input field, `2xSpace` will answer the call

2. **Ending a call**
   * During a call or incoming call, if you are not in an input field, double pressing `SYM` will end the call

---

### Acknowledgments

* Founder: Artem Tverdokhleb aka l3n1n-ua (UA)
* Co-author: krab-ubica (RUS)
* Co-author: Henry2005 aka CapitanNemo (BY)
