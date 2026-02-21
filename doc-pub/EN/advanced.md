# Advanced

## Description of advanced settings and keyboard customizations

> **Disclaimer.** Careless and inattentive actions can lead to keyboard malfunction, and figuring out what happened without looking at the code will be extremely difficult.
>
> For example, forgetting to add a bracket in JSON (believe me, people have spent dozens of hours on such mistakes)

---

### Search plugins (basics)

> These are mini-bots that, when you enter an application and immediately start typing on the hardware keyboard, press the search button (which is usually at the top) for you. Incredibly convenient. How did I live without this before.

* The list of current search plugins built into the application can be viewed by going to the Extended Settings menu
* If you don't find the application you'd like to "automate" in terms of clicking the "magnifying glass", it's quite easy to do:
  1. Enter "your" application;
  2. Go to K12KB Extended Settings;
  3. Find your application among the three ADD PLUGIN buttons and click on it -- the button will change to ADDED;

> The ADD PLUGIN buttons show the 3 most recent applications you visited before entering K12KB extended settings. So it's best to go to K12KB Extended Settings right after entering the application (via "alt+tab" or "recent apps").

* A Toast will pop up. The accessibility service will turn off automatically. You need to press the Activate Accessibility button to enable the service.
* Enter your application and type any letter (preferably in the Cyrillic layout), if search opened and the letter you typed appeared in it -- Hooray! Your application falls into the 80% of simple cases;

> If you enter a character in the Latin layout, there's a chance the application handles character input itself and opens search, but only for Latin characters. That doesn't quite suit us. So we test search plugins with Cyrillic.

#### How to verify the result

It should work in the application immediately, but if you want to double-check:

* Go to K12KB extended settings, scroll down, find your application and after ResourceId: the search field code.

> Or after ResourceId instead of the search field code you see two paragraphs of text, but the search plugin in the new application still works. Don't worry. This is a slightly more complex case, described below.

#### Saving plugin data

> **IMPORTANT!** You must save the data, otherwise when the phone restarts you'll need to add plugins again.

1. In either case, press SAVE PLUGIN DATA
2. Go to the folder `/storage/emulated/0/K12Kb/default`
3. Take the file `plugin_data.json` and place it in `/storage/emulated/0/K12Kb`
4. From this folder, K12KB will pick it up automatically instead of the analogous file embedded in the application resources

> **Important!** If you uninstall the application, all configuration files will be deleted along with the folder. Make a backup.

To pick up the new file, you need to restart the Accessibility Service (K12KB).

---

### Search plugins (more complex cases)

> I haven't found cases where you'd need to modify the keyboard's source code to add a new application to the search plugin. But if you've tried everything listed below, "welcome to DMs"

**Description will follow later, for now here's what's possible:**

1. Add your own "search" words to the universal search words list

   `"default-search-words" : [ "Найти", "Поиск", "Search", "Искать" ]`

2. Add a delay (Xms) after clicking Search but before entering the character (for cases where search "slides in" or is just "slow");

3. Click not on the element with the Search text itself, but on its "parent" (for cases where clicking the element itself doesn't work);

```
"package-name" : "ru.yandex.yandexmaps",
"custom-click-adapter-click-parent" : true,
"search-field-id" : "ru.yandex.yandexmaps:id/search_line_search_text",
"wait-before-send-char-ms" : 300
```

4. For so-called dynamic Search buttons (which don't provide their name for saving), leave only one search method to avoid trying all 10 universal methods each time (as done for Telegram, for example);

```
"package-name" : "org.telegram.messenger",
 "additional-event-type-type-window-content-changed" : true,
 "dynamic-search-method" : [ {
   "dynamic-search-method-function" : "FindAccessibilityNodeInfosByText",
   "contains-string" : "Search" } ]
```

5. Remove unnecessary event types if the application is lagging;

6. Maximum settings are shown for the `org.example.dummy` application (useful for copy-paste):

```
"package-name" : "org.example.dummy",
 "additional-event-type-type-window-content-changed" : true,
 "custom-click-adapter-click-parent" : true,
 "dynamic-search-method" : [ {
   "dynamic-search-method-function" : "FindFirstByTextRecursive",
   "contains-string" : "Найти"
 }, {
   "dynamic-search-method-function" : "FindAccessibilityNodeInfosByText",
   "contains-string" : "Поиск"
 } ],
 "wait-before-send-char-ms" : 200
```

---

### Search plugins (known issues)

> When adding a new application to plugins and entering the application for the first time, it is **IMPORTANT** that the application is NOT in activated search mode at that moment, otherwise the wrong search field identifiers may be saved and it won't work.

**Fix: The "Clear plugin data" button at the bottom**

* Deletes search field identifiers of applications;
* But in fact, nothing is deleted from applications already registered in `plugin_data.json` files -- everything will be restored after restarting the accessibility service;
* However, for freshly added applications where everything was identified incorrectly -- this is the solution;
* The main thing is, once everything works correctly, don't forget to save to a file and place it where needed (described above).

---

### Keyboard layout customization

> **Important!** If you uninstall the application, all custom layouts will be deleted along with the folder. Make a backup.

1. Go to the "EXTENDED SETTINGS" menu item
2. Press "SAVE KEYBOARD DATA"
3. Layout data will be saved to the folder `/storage/emulated/0/K12Kb/default`

> **(!)** If you press "SAVE KEYBOARD DATA" again, the files in the `default` folder **will be overwritten**

4. JSON files will appear in the folder:
   * Letter layouts in files like `russian_hw.json` or `pocket_english.json`
   * Symbol layouts in files like `alt_hw.json` or `pocket_alt_hw.json`

> The file contents are self-explanatory.

5. Modify the letter or symbol layout file
6. Place it in the folder `/storage/emulated/0/K12Kb`
   * By default, all layouts are embedded in application resources
   * But if the keyboard finds a file with the same name in its folder on disk during startup, it preferentially uses that file

> If you haven't modified the file, there's no point keeping it on disk -- it loads slightly slower from disk

> **Important!** If you uninstall the application, all custom layouts will be deleted along with the folder. Make a backup.

#### Custom symbols per layout

If you want a custom symbol from the symbol layout that will be specific to a letter layout, you can add fields from the symbol layout to the letter layout and they will be picked up and used by default. For example, in the Russian layout, replace the dollar sign with the ruble sign.

`russian_hw.json`:

```
  "keyboard-name" : "Русский",
  "alt-mode-layout" : "alt_hw",
  "sym-mode-layout" : "symbol",
  "key-mapping" : [ {
    "key-code" : 11,
    "single-press" : "б",
    "single-press-shift-mode" : "Б",
    "double-press" : "ю",
    "double-press-shift-mode" : "Ю",
    "single-press-alt-mode" : "₽",
    "single-press-alt-shift-mode" : "$",
    "alt-more-variants" : "€₽$€",
    "alt-shift-more-variants" : "€₽$"
```

> **Note:** `"sym-mode-layout" : "symbol"` -- this is the on-screen symbol panel, dynamically populated with symbols from the alt layout. Do not modify this for now.

#### keyboard\_layouts.json

The file contains the complete list of layouts.

* All layouts are listed in it along with some options
* Layouts are taken from it and dynamically rendered in settings with an on/off toggle
* This file can also be modified and will be picked up from disk by default

> For example, you can remove unnecessary layouts if their presence in the menu is annoying. Or change the name in the menu.

* Icons cannot be changed -- they are embedded in resources. But if you use a resource editor, you can (just make sure to duplicate them in both mipmap and drawable folders).
* Layouts for other devices and languages can be added, but remember to create their own letter and symbol mapping files (described above)
* The reference to the symbol layout is made from the letter layout. To avoid duplicating the same thing 500 times.

> For example, all language layouts (and there are four) for Blackberry Key1-2 share the same symbol layout `alt_hw`

`russian_hw.json`:

```
    "keyboard-name" : "Русский",
    "alt-mode-layout" : "alt_hw"
```

* Example: Fragment of the `alt_hw.json` symbol layout

```
  "key-code" : 11,
  "single-press-alt-shift-mode" : "€",
  "single-press-alt-mode" : "$",
  "alt-more-variants" : "₽₴₩£₪¥$€",
  "alt-shift-more-variants" : "₽₴₩£₪¥$€"
```

#### Different layouts can have their own keyboard mechanics

```
{
  "options-name" : "Pocket Русский",
  "keyboard-mapping" : "pocket_russian_hw",
  "icon-lowercase" : "ic_rus_small",
  "icon-first-shift" : "ic_rus_shift_first",
  "icon-capslock" : "ic_rus_shift_all",
  "custom-keyboard-mechanics": "keyboard_mechanics_pocket"
} ]
```

---

### Core constants customization

**All file mechanics are analogous to layouts (see above)**

`keyboard_core.json`:

```
{
  "time-short-press" : 200,
  "time-double-press" : 400,
  "time-long-press" : 300,
  "time-long-after-short-press" : 600,
  "time-wait-gesture-upon-key0-hold" : 1000,
  "gesture-finger-press-radius" : 45,
  "gesture-motion-base-sensitivity" : 48,
  "gesture-row4-begin-y" : 415,
  "gesture-row1-begin-y" : 25,
  "time-vibrate" : 30
}
```

#### Timing constants

* `TIME_SHORT_PRESS` -- time from button (tap) press to release (first time)
* `TIME_DOUBLE_PRESS` -- time from FIRST button (tap) press to SECOND press
* `TIME_TRIPLE_PRESS` -- time from SECOND button (tap) press to THIRD press

---

### Keyboard logic customization

**All file mechanics are analogous to layouts (see above)**

> **Important!** Different layouts can have their own keyboard mechanics.

```
{
  "options-name" : "Pocket Русский",
  "keyboard-mapping" : "pocket_russian_hw",
  "icon-lowercase" : "ic_rus_small",
  "icon-first-shift" : "ic_rus_shift_first",
  "icon-capslock" : "ic_rus_shift_all",
  "custom-keyboard-mechanics": "keyboard_mechanics_pocket"
} ]
```

#### keyboard\_mechanics.json

```
      "key-codes": [
        "KEYCODE_DEL"
      ],
      "on-short-press": [
        {
          "meta-mode-method-names": [
            "IsActionBeforeMeta"
          ],
          "action-method-name": "ActionTryVibrate"
        },
        {
          "action-method-name": "ActionKeyDownUpDefaultFlags",
          "custom-key-code": "KEYCODE_DEL"
        }, {
          "action-method-name": "ActionTryTurnOffGesturesMode"
        }, {
          "action-method-name": "ActionResetDoubleClickGestureState",
          "need-update-gesture-visual-state": true
        }, {
          "meta-mode-method-names": [
            "MetaIsAltPressed"
          ],
          "action-method-name": "ActionDeleteUntilPrevCrLf",
          "stop-processing-at-success-result": true
        }, {
          "meta-mode-method-names": [
            "MetaIsShiftPressed"
          ],
          "action-method-name": "ActionKeyDownUpDefaultFlags",
          "stop-processing-at-success-result": true,
          "custom-key-code": "KEYCODE_FORWARD_DEL"
        }
      ]
    }
```

#### Base elements

##### `meta-mode-method-names` (array)

```
{
          "meta-mode-method-names": [
            "MetaIsCtrlPressed"
          ],
          "action-method-name": "ActionSendCtrlPlusKey",
          "method-needs-key-press-parameter": true,
          "stop-processing-at-success-result": true
        }
```

* This is needed to execute a method in the body under a certain condition (conditions)
* If there are multiple methods in the array, both must succeed (return true) for the "body" to execute

```
{
          "meta-mode-method-names": [
            "MetaIsAltMode", "MetaIsShiftPressed"
          ],
          "action-method-name": "ActionSendCharSinglePressSymMode",
          "method-needs-key-press-parameter": true,
          "stop-processing-at-success-result": true
        }
```

* If you need to execute multiple functions in the body, the `"meta-mode-method-names"` entry is duplicated for each body function

```
{
          "meta-mode-method-names": [
            "MetaIsAltMode"
          ],
          "action-method-name": "ActionSendCharToInput",
          "stop-processing-at-success-result": false,
          "custom-char": "0"
        }, {
          "meta-mode-method-names": [
            "MetaIsAltMode"
          ],
          "action-method-name": "ActionTryTurnOffGesturesMode",
          "need-update-gesture-visual-state": true,
          "stop-processing-at-success-result": false
        }
```

> **Important!** The last body method must stop further execution (`"stop-processing-at-success-result": true`). Otherwise, all methods from the unconditional block (without meta conditions) will be executed next.

##### `stop-processing-at-success-result`

* This flag is used to stop further execution of body methods
* If the flag is not set, it defaults to `false`

##### `IsActionBeforeMeta`

* This is a fake meta method that always returns `true`
* It's needed to call some action before conditional meta state handlers (i.e., for cases without meta states as well)

```
      "on-short-press": [
        {
          "meta-mode-method-names": [
            "IsActionBeforeMeta"
          ],
          "action-method-name": "ActionTryVibrate"
        },
```

##### `method-needs-key-press-parameter`

This is needed for methods that use KeyEvent data during execution.

> No need to get creative here -- if it was set (for example, for the `ActionSendCharSinglePressNoMeta` method) in the default `keyboard_mechanics`, it should stay that way.

Similarly for `custom-key-code` and `custom-char`.

##### `need-update-visual-state`

After processing the function "body", the keyboard icon update method will be called (layout, alt-mode, etc.)

> Don't set these flags unnecessarily, as screen update is a resource-intensive operation.

##### `need-update-gesture-visual-state`

After processing the function "body", the gesture mode icon update method will be called (text field gesture mode, vertical, etc.)

> Don't set these flags unnecessarily, as screen update is a resource-intensive operation.

#### key-group-processors -- button press handler configuration

##### Keycode configuration

This is an array which is iterated through on button press (as soon as something matches, we enter the body). Values can be KEYCODEs from [KeyEvent](https://developer.android.com/reference/android/view/KeyEvent):

```
      "key-codes": [
        "KEYCODE_0"
      ],
```

Or numeric code values:

```
      "key-codes": [
        "287"
      ],
```

> The keyboard works on a quick input principle (i.e., a single event fires immediately without waiting for double press or long press). As practice has shown: 1. This works noticeably faster and is more usable 2. Undoing a single press input has never been a problem

##### Event types

* `on-short-press` -- for a single short press
* `on-double-press` -- for two quick short presses
* `on-hold-on` -- for holding down (before releasing), used in hold+button mode
* `on-hold-off` -- for releasing a hold, used in hold+button mode
* `on-long-press` -- for a long press

> `on-hold-xxx` and `on-long-press` are mutually exclusive events, only one should be used.

**Roughly speaking, there are 2 modes of hardware button operation:**

1. **As a meta button** (with hold+other button capability). Events: `on-short-press`, `on-double-press`, `on-hold-xxx`
2. **As a regular letter/digit button** (with long press capability). Events: `on-short-press`, `on-double-press`, `on-long-press`

##### Event handler execution order (`on-xxx...`)

* Conditional constructs (`"meta-mode-method-names"`) can be used
* All conditional constructs are called in order
* The body (function) of each triggered conditional meta method (that returned true) is executed
  * When a stop flag (`"stop-processing-at-success-result"`) is encountered, event processing stops
* If there was no stop flag or no conditional meta method triggered,
  then the so-called unconditional block is executed

```
      "on-short-press": [
        {
          "meta-mode-method-names": [
            "IsActionBeforeMeta"
          ],
          "action-method-name": "ActionTryVibrate"
        },
        {
          "action-method-name": "ActionChangeKeyboardLayout",
          "need-update-visual-state": true,
          "stop-processing-at-success-result": true
        }
```

#### Other entities in keyboard\_mechanics.json

##### `on-start-input-actions`

* Actions executed when the keyboard enters an input field
* Conditional constructs (`"meta-mode-method-names"`) can be used

Typically involves enabling certain modes and disabling others.

##### `on-finish-input-actions`

* Actions executed when the keyboard exits an input field
* Conditional constructs (`"meta-mode-method-names"`) can be used

Typically involves resetting certain modes.

##### `before-send-char-actions`

* Actions executed before sending a character to the input field
* Conditional constructs (`"meta-mode-method-names"`) can be used

Currently only launches the search plugin here.

##### `after-send-char-actions`

* Actions executed after sending a character to the input field
* Conditional constructs (`"meta-mode-method-names"`) can be used

Resets various modes that change after character input.

##### `view-mode-key-transparency-exclude-key-codes`

* KEYCODEs that are sent to the keyboard even when not in an input field

Typically these are keyboard mode changes (NAV, language, etc.)

* All other keycodes in viewing mode are ignored by the keyboard (corresponding events are sent to the host application as-is)

##### `gesture-processor`

Settings for keyboard gesture mode handlers (TBD).

---

### Accessibility service customization

**All file mechanics are analogous to layouts (see above)**

`k12kb_as_options.json`:

```
{
  "search-plugins-enabled": true,
  "retranslate-keyboard-key-codes": [
    "KEYCODE_FUNCTION"
  ],
  "retraslate-keyboard-meta-key-plus-key-list": [
    {
      "meta-key-code": "META_FUNCTION_ON",
      "key-key-code": "KEYCODE_A"
    }, {
      "meta-key-code": "META_FUNCTION_ON",
      "key-key-code": "KEYCODE_C"
    }, {
      "meta-key-code": "META_FUNCTION_ON",
      "key-key-code": "KEYCODE_V"
    }, {
      "meta-key-code": "META_FUNCTION_ON",
      "key-key-code": "KEYCODE_X"
    }, {
      "meta-key-code": "META_FUNCTION_ON",
      "key-key-code": "KEYCODE_Z"
    }
  ]
}
```

#### `search-plugins-enabled`

Search plugins on/off.

#### `retranslate-keyboard-key-codes`

For buttons that the keyboard doesn't "catch" but the accessibility service does. Such events are relayed from the accessibility service to the keyboard (the keyboard treats them like any other events).

> For example, the BlackBerry Key2 speed\_key (`KEYCODE_FUNCTION`) is not caught by the keyboard, or the side button `"287"`.

#### `retraslate-keyboard-meta-key-plus-key-list`

Relay from the accessibility service to the keyboard: meta\_hold + button event (the keyboard treats them like any other events). For cases when the meta\_hold event is not passed to the keyboard but is caught by the accessibility service.

> For example, the BlackBerry Key2 speed\_key meta state (`"meta-key-code": "META_FUNCTION_ON"`) is not caught by the keyboard.
