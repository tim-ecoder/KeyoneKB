---
metaLinks:
  alternates:
    - /broken/spaces/enrWYfvkTAnmCaV7Jpd9/pages/MMDgpeqZVovq5NLvKJkV
---

# Advanced

## Описание сложных настроек и кастомизаций клавиатуры

#### Disclamer. Невнимательные и неосторожные действия могут привести неработоспособности клавиатуры и разобраться, что случилось, не залезая в код, будет весьма сложно.

> Например какую-то скобку забыть поставить в json (поверьте, на подобные косяки люди тратили десятки часов)

###Поисковые плагины (основное)

> Это мини-боты, которые, когда ты зайдя в приложение сразу начинаешь набирать на хардварной клаве, нажимают кнопку поиск (которая обычно сверху) за тебя. Офигенски удобно. Как я раньше жил без этого.

* Список текущих поисковых плагинов, которые встроены в приложение, можно посмотреть зайдя в меню Расширенные настройки
* Если вы не обнаружили в списке приложение, которое хотели бы "автоматизировать" в части клика на "лупу", то это достаточно легко можно сделать:
* Зайдите в "свое" приложение;
* Перейдите в расширенные настройки KeyoneKb;
* Найдите среди трех кнопок ADD PLUGIN свое приложение и нажмите на него кнопка станет ADDED;

> Кнопки ADD PLUGIN берут 3 последних приложения, в которые вы заходили до входа в расширенные настройки Keyonekb. Поэтому лучше сразу после входа в приложение зайти в расширенные настройки KeyoneKb (через "alt+tab" или "последние приложения").

* Выскочит Toast. Сервис спец. возможностей отключится автоматически. Надо нажать на кнопку Активировать спец. возможности, чтобы включить сервис.
* Зайдите в свое приложение и введите любую букву (желательно в кириллической раскладке), если открылся поиск и в нем оказалась введенная вами буква - Ура! ваше приложение попало в 80% простых случаев;

> Если вводить символ в латинской раскладке, есть вероятность что приложение само обрабатывает ввод символов и открывает поиск, но только для латиницы. Нам это не совсем подходит. Поэтому тестируем поисковые плагины на кириллице.

**Как посмотреть, что получилось (должно сразу по факту работать в приложении, но если хочется еще разок убедиться)**

* Заходим в расширенные настройки keyonekb2, пролистываем вниз, видим там наше приложение и после ResourceId: код поискового поля.

> Либо после ResourceId вместо кода поискового поля мы видим текст на два абзаца, но при этом поисковый плагин в новом приложении все-таки работает. Пугаться не стоит. Это чуть более сложный случай, про него будет дальше.

**ВАЖНО! Надо сохранить данные, иначе при перезапуске телефона придется заново добавлять плагины**

. В любом из обоих случаев жмем SAVE PLUGIN DATA . Идем в папку /storage/emulated/0/Android/data/com.sateda.keyonekb2/files/default . Берем файл plugin\_data.json и кладем его в /storage/emulated/0/Android/data/com.sateda.keyonekb2/files . Из этой папки KeyoneKb2 будет подхватывать автоматически вместо аналогичного файла, зашитого в ресурсы приложения

#### Важный момент! Если снести приложение то все настроечные файлы удалятся вместе с папкой. Делайте бэкап.

***

### Чтобы новый файл переподхватился надо перезапустить Accessibility Service (KeyoneKb2)

###Поисковые плагины (более сложные случаи)

> Я пока не нашел случаев, когда приходилось бы лезть в программный код клавиатуры, чтобы добавить какие-то новое приложение в поисковый плагин. Но если вы попробовали все ниже перечисленное, то "велком в личку"

**Описание будет чуть позже, пока перечислю что можно**

. Добавлять свои "поисковые" слова в список универсальных поисковых слов

`"default-search-words" : [ "Найти", "Поиск", "Search", "Искать" ]`

. Делать паузу (Xms) после нажатия на Поиск, но перед вводом символа (для случаев, когда поиск "выезжает" или просто "тупит"); . Делать клик не на сам элемент с текстом Поиск, а на его "родителя" (для случаев, когда на сам элемент тычок не работает);

"package-name" : "ru.yandex.yandexmaps", "custom-click-adapter-click-parent" : true, "search-field-id" : "ru.yandex.yandexmaps:id/search\_line\_search\_text", "wait-before-send-char-ms" : 300

. Для т.н. динамических кнопок Поиск (которые не отдают свое имя для сохранения) оставлять только один поисковый метод, чтобы каждый раз не пробовать все 10 универсальных метода (как сделано для Telegram например);

"package-name" : "org.telegram.messenger", "additional-event-type-type-window-content-changed" : true, "dynamic-search-method" : \[ { "dynamic-search-method-function" : "FindAccessibilityNodeInfosByText", "contains-string" : "Search" } ]

. Убирать лишние типы евентов, если приложение подтормаживает; . Максимальные настройки показаны для приложения org.example.dummy (полезно для копипаста)

"package-name" : "org.example.dummy", "additional-event-type-type-window-content-changed" : true, "custom-click-adapter-click-parent" : true, "dynamic-search-method" : \[ { "dynamic-search-method-function" : "FindFirstByTextRecursive", "contains-string" : "Найти" }, { "dynamic-search-method-function" : "FindAccessibilityNodeInfosByText", "contains-string" : "Поиск" } ], "wait-before-send-char-ms" : 200

###Поисковые плагины (известные затруднения)

#### Когда добавляешь новое приложение в плагины и первый раз заходишь в приложение, ВАЖНО, чтобы приложение не было в этот момент в режиме активированного поиска, иначе могут запомниться не те идентификаторы поисковых полей и работать не будет

**Лечение: внизу кнопка "Clear plugin data"**

* Удаляет идентификаторы Поисковых полей приложений;
* Но по факту ничего не удаляет из приложений, которые уже прописаны в файлах plugin\_data.json, в них все восстановится после перезагрузки спец. сервиса;
* Зато для свеже-добавленных приложений, если там неправильно все определилось - это спасение;
* Главное, когда уже все правильно получилось, не забыть сохраниться в файл и подложить его куда следует (выше писал)

###Кастомизация раскладок клавиатуры

#### Важный момент! Если снести приложение то все кастомные раскладки удалятся вместе с папкой. Делайте бэкап.

. Зайти в пункт меню "РАСШИРЕННЫЕ НАСТРОЙКИ" . Нажать "SAVE KEYBOARD DATA" . Данные о раскладках сохранятся в папке /storage/emulated/0/Android/data/com.sateda.keyonekb2/files/default

> (!) Если повторно нажать на кнопку "SAVE KEYBOARD DATA" то файлы в папке /default **перезапишутся**

. В папке появятся файлы \*.json

* Сами раскладки для букв в файлах типа russian\_hw.json или pocket\_english.json
* Символьные раскладки в файлах типа alt\_hw.json или pocket\_alt\_hw.json

> Содержимое файлов описывать не имеет смысла там все понятно.

. Изменить файл с буквенной или символьной раскладкой . Записать его в папку /storage/emulated/0/Android/data/com.sateda.keyonekb2/files

* Базово все раскладки зашиты в ресурсы приложения
* Но, если клава при включении видит файл с таким же названием в "своей" папке на диске она предпочтительно берет его

> Если вы не поменяли файл то смысла его держать на диске нет, с диска чуть дольше загружается

#### Важный момент! Если снести приложение то все кастомные раскладки удалятся вместе с папкой. Делайте бэкап.

. **Hint** Если вы хотите кастомный символ из символьной раскладки, который будет специфичен для раскладки. То из символьной раскладки можно добавить поля в буквенную и они подхватятся и будут браться по умолчанию. Например, в русской раскладке на доллар повесить знак рубля.

### .russian\_hw.json \[source,javascript]

### "keyboard-name" : "Русский", "alt-mode-layout" : "alt\_hw", "sym-mode-layout" : "symbol", "key-mapping" : \[ { "key-code" : 11, "single-press" : "б", "single-press-shift-mode" : "Б", "double-press" : "ю", "double-press-shift-mode" : "Ю", "single-press-alt-mode" : "₽", "single-press-alt-shift-mode" : "$", "alt-more-variants" : "€₽$€", "alt-shift-more-variants" : "€₽$"

**"sym-mode-layout" : "symbol" - это наэкранная символьная панель, динамически заполняется символами из alt-раскладки. Трогать это пока нельзя.**

### **keyboard\_layouts.json**

Файл содержит общий список раскладок

* В нем перечисляются все раскладки и некоторые опции для них
* Из него берутся раскладки и динамически прорисовываются в настройках с переключателем вкл-выкл который
* Этот файл тоже может быть изменен и будет подхватываться с диска по умолчанию

> Например, в нем можно например убрать ненужные раскладки, если их наличие они бесит в меню. Или название в меню поменять.

* Иконки менять нельзя, они зашиты в ресурсы. Но если вооружиться редактором ресурсов то можно (главное продублировать их в две папки mipmap и drawable).
* Раскладки для других устройств и языков можно добавлять, но важно не забыть для них сделать свои файлы маппинга букв и символов (описано выше)
* Ссылка на символьную раскладку делается из буквенной раскладки. Чтобы не дублировать одно и тоже 500 раз.

> Например, для всех языков-раскладок (а их четыре) Blackberry Key1-2 одна и та же символьная раскладка alt\_hw

### .russian\_hw.json \[source,javascript]

```
"keyboard-name" : "Русский",
"alt-mode-layout" : "alt_hw"
```

***

* Пример. Фрагмент символьной раскладки alt\_hw.json .russian\_hw.json \[source,javascript]

***

### "key-code" : 11, "single-press-alt-shift-mode" : "€", "single-press-alt-mode" : "$", "alt-more-variants" : "₽₴₩£₪¥$€", "alt-shift-more-variants" : "₽₴₩£₪¥$€"

#### Важно! Для разных раскладок можно сделать свою механику клавиатуры

***

### { "options-name" : "Pocket Русский", "keyboard-mapping" : "pocket\_russian\_hw", "icon-lowercase" : "ic\_rus\_small", "icon-first-shift" : "ic\_rus\_shift\_first", "icon-capslock" : "ic\_rus\_shift\_all", "custom-keyboard-mechanics": "keyboard\_mechanics\_pocket" } ]

###Кастомизация ядровых констант

**Вся механика с файлом аналогична раскладкам (см. выше)**

### .keyboard\_core.json \[source,javascript]

### { "time-short-press" : 200, "time-double-press" : 400, "time-long-press" : 300, "time-long-after-short-press" : 600, "time-wait-gesture-upon-key0-hold" : 1000, "gesture-finger-press-radius" : 45, "gesture-motion-base-sensitivity" : 48, "gesture-row4-begin-y" : 415, "gesture-row1-begin-y" : 25, "time-vibrate" : 30 }

```
        TIME_SHORT_PRESS - время от нажатия кнопки(тапа) до отжатия (первый раз)
        TIME_DOUBLE_PRESS - время от нажатия кнопки(тапа) ПЕРВЫЙ раз до нажатия ВТОРОЙ раз
        TIME_TRIPLE_PRESS - время от нажатия кнопки(тапа) ВТОРОЙ раз до нажатия ТРЕТИЙ раз
```

###Кастомизация логики клавиатуры

**Вся механика с файлом аналогична раскладкам (см. выше)**

#### Важно! Для разных раскладок можно сделать свою механику клавиатуры

***

### { "options-name" : "Pocket Русский", "keyboard-mapping" : "pocket\_russian\_hw", "icon-lowercase" : "ic\_rus\_small", "icon-first-shift" : "ic\_rus\_shift\_first", "icon-capslock" : "ic\_rus\_shift\_all", "custom-keyboard-mechanics": "keyboard\_mechanics\_pocket" } ]

### .keyboard\_machanics.json \[source,javascript]

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

***

### Базовые элементы

#### meta-mode-method-names (массив)

***

### { "meta-mode-method-names": \[ "MetaIsCtrlPressed" ], "action-method-name": "ActionSendCtrlPlusKey", "method-needs-key-press-parameter": true, "stop-processing-at-success-result": true }

* Это нужно чтобы запустить метод в теле при каком-то условии (условиях)
* Если в массиве несколько методов, то чтобы выполнилось "тело" они оба должны сработать (вернуть true)

***

### { "meta-mode-method-names": \[ "MetaIsAltMode", "MetaIsShiftPressed" ], "action-method-name": "ActionSendCharSinglePressSymMode", "method-needs-key-press-parameter": true, "stop-processing-at-success-result": true }

* Если нужно запустить несколько функций в теле, то запиcь meta-mode-method-names дублируется для каждой функции тела

***

### { "meta-mode-method-names": \[ "MetaIsAltMode" ], "action-method-name": "ActionSendCharToInput", "stop-processing-at-success-result": false, "custom-char": "0" }, { "meta-mode-method-names": \[ "MetaIsAltMode" ], "action-method-name": "ActionTryTurnOffGesturesMode", "need-update-gesture-visual-state": true, "stop-processing-at-success-result": false }

* Важно! Последний метод тела должен быть останавливающим дальнейшее выполнение ("stop-processing-at-success-result": true)
* Иначе следом будут выполняться все методы из безусловного блока (без meta условий)

#### stop-processing-at-success-result

* Флаг используется для остановки дальнейшего выполнения методов тела
* Если флаг он не установлен то он по умолчанию false

#### IsActionBeforeMeta

* Это фейк-мета метод, всегда возвращающий true
* Он нужен, чтобы вызвать какое-то действие перед условными обработчиками мета состояний (т.е. и для случаев без мета состояний)

***

```
  "on-short-press": [
    {
      "meta-mode-method-names": [
        "IsActionBeforeMeta"
      ],
      "action-method-name": "ActionTryVibrate"
    },
```

***

#### method-needs-key-press-parameter

**Это нужно для методов, которые в процессе исполнения используют данные KeyEvent**

> Тут фантазировать ничего не надо - если оно было установлено (например для метода ActionSendCharSinglePressNoMeta) в дефолтной keyboard\_mechanics значит оно так и должно остаться

**Аналогично для custom-key-code и custom-char**

#### need-update-visual-state

**После обработки "тела" функции будет вызван метод изменения иконки клавиатуры (раскладка, alt-режим и пр.)**

> Лишний раз эти флаги ставить не следует т.к. обновление экрана - ресурсозатратная операция

#### need-update-gesture-visual-state

**После обработки "тела" функции будет вызван метод изменения иконки режима жестов (режим жестов по текстовому полю, вертикальный и пр.)**

> Лишний раз эти флаги ставить не следует т.к. обновление экрана - ресурсозатратная операция

### key-group-processors - настройка обработчиков нажатий на кнопки

* Настройка keycode-ов \*\* Это массив - который при нажатии кнопки "пробуется" перебором (как только что-то совпало входим в тело) \*\* Могут быть KEYCODE-ы из https://developer.android.com/reference/android/view/KeyEvent\[KeyEvent]

***

```
  "key-codes": [
    "KEYCODE_0"
  ],
```

***

### \*\* Либо циферные значения кодов

```
  "key-codes": [
    "287"
  ],
```

***

> Клавиатура работает по принципу быстрого ввода (т.е. одиночное событие срабатывает и не ждет двойное нажатие или долгое нажатие). Как показала практика: 1. Так работает ощутимо быстрее и юзабильнее 2. Отменить ввод единичного нажатия ни разу не было проблемой

* on-short-press для единичного короткого нажатия
* on-double-press для двух быстрых коротких нажатий
* on-hold-on для зажатия (до отпускания) (используется в режиме зажатие+кнопка)
* on-hold-off для отпускания зажатия (используется в режиме зажатие+кнопка)
* on-long-press для долгого нажатия

> on-hold-xxx и on-long-press взаимоисключающие события, должно быть что-то одно

**Грубо говоря есть 2 режима работы хардварной кнопки:**

1. Как мета-кнопка (с возможностью зажатие+другая кнопка). События: on-short-press, on-double-press, on-hold-xxx
2. Как обычная кнопка буквы-цифры (с возможностью долгого нажатия). События: on-short-press, on-double-press, on-long-press

**Обработчики событий (on-xxx...):**

* Могут применяться условные конструкции (meta-mode-method-names)
* Все условные конструкции вызываются по порядку
* Тело (функция) каждого сработавшего условного-мета-метода (вернувшего true) выполняется \*\* Когда встретится стоп-флаг (stop-processing-at-success-result) обработка события останавливается
* Если не было стоп-флага или не было ни одного сработавшего условного-мета-метода
* То выполняется т.н. безусловный блок

***

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

***

### Прочие сущности в keyboard\_mechanics.json

#### on-start-input-actions

* Действия, выполняющиеся, когда клавиатура попадает в поле ввода
* Могут применяться условные конструкции (meta-mode-method-names)

***

### Как правило речь идет о включении того или иного режима и отключении каких-то режимов

#### on-finish-input-actions

* Действия, выполняющиеся, когда клавиатура выходит из поля ввода
* Могут применяться условные конструкции (meta-mode-method-names)

***

### Как правило речь идет о сбросе каких-то режимов

#### before-send-char-actions

* Действия, выполняющиеся перед отправкой символа в поле ввода
* Могут применяться условные конструкции (meta-mode-method-names)

***

### Пока тут только запуск поискового плагина

#### after-send-char-actions

* Действия, выполняющиеся после отправки символа в поле ввода
* Могут применяться условные конструкции (meta-mode-method-names)

***

### Сбросы разных режимов, которые меняются после ввода символа

#### view-mode-key-transparency-exclude-key-codes

* KEYCODE-ы, которые отправляются в клавиатуру даже если мы не находится в поле ввода

***

### Как правило - это изменение режимов клавиатуры (NAV, язык и пр.)

* Все остальные keycode-ы в режиме чтения игнорируются клавиатурой (соответствующие события отправляются в хост-приложение как есть)

#### gesture-processor

**Настройки для обработчиков режима жестов по клавиатуре (TBD)**

###Кастомизация спец. сервиса (accessibility service)

**Вся механика с файлом аналогична раскладкам (см. выше)**

### .keyonekb2\_as\_options.json \[source,javascript]

### { "search-plugins-enabled": true, "retranslate-keyboard-key-codes": \[ "KEYCODE\_FUNCTION" ], "retraslate-keyboard-meta-key-plus-key-list": \[ { "meta-key-code": "META\_FUNCTION\_ON", "key-key-code": "KEYCODE\_A" }, { "meta-key-code": "META\_FUNCTION\_ON", "key-key-code": "KEYCODE\_C" }, { "meta-key-code": "META\_FUNCTION\_ON", "key-key-code": "KEYCODE\_V" }, { "meta-key-code": "META\_FUNCTION\_ON", "key-key-code": "KEYCODE\_X" }, { "meta-key-code": "META\_FUNCTION\_ON", "key-key-code": "KEYCODE\_Z" } ] }

#### search-plugins-enabled (поисковые плагины вкл/выкл)

#### retranslate-keyboard-key-codes

**Для кнопок, которые не "ловит" клавиатура, но ловит спец. сервис**

**Такие события передаются из спец. сервиса в клавиатуру (клавиатура не отличает их от всех других)**

> Например speed\_key blackberry key2 (KEYCODE\_FUNCTION) не ловится клавиатурой или боковая кнопка "287"

#### retraslate-keyboard-meta-key-plus-key-list

**Ретрансляция из спец. сервиса в клавиатуру события мета\_зажатие + кнопка (клавиатура не отличает их от всех других)**

**Для случая, когда событие мета\_зажатие не передается в клавиатуру, но ловится спец. сервисом**

> Например мета-состояние speed\_key blackberry key2 ("meta-key-code": "META\_FUNCTION\_ON",) не ловится клавиатурой
