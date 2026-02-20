# K12KB — Анализ автоматизированного тестирования на устройстве

## 1. Введение

Данный документ анализирует возможности автоматизированного тестирования K12KB IME непосредственно на устройстве, включая стратегии стаббирования системных событий (KeyEvent, MotionEvent, AccessibilityEvent), архитектуру тестового фреймворка и рекомендации по инструментам.

### Специфика тестирования IME

IME (InputMethodService) — особый компонент Android: он работает в отдельном процессе, получает события ввода через системный `InputMethodManager`, и взаимодействует с приложениями через `InputConnection`. Это создаёт уникальные сложности для автотестирования:

- IME не является Activity — стандартные ActivityTestRule/ActivityScenario не применимы напрямую
- `KeyEvent` приходит через системный фреймворк, а не через UI-тесты
- `MotionEvent` от сенсора клавиатуры проходит через `onGenericMotionEvent()`, а не через стандартный touch
- Результат работы IME (текст) видимый через `InputConnection` целевого приложения

---

## 2. Точки входа для инъекции событий

### 2.1 Аппаратные клавиши — KeyEvent

**Текущая архитектура:**
```
Системный KeyEvent
  → K12KbIME.onKeyDown(keyCode, KeyEvent)
    → InputMethodServiceCoreKeyPress.processKeyDown()
      → KeyDownList1 (активные клавиши)
      → Таймеры (short/double/triple/long press)
    → InputMethodServiceCoreCustomizable.executeAction()
      → ~50 методов-действий через рефлексию
    → InputConnection.commitText() / sendKeyEvent()
```

**Стратегия стаббирования:**

| Уровень | Метод | Плюсы | Минусы |
|---------|-------|-------|--------|
| **Системный** | `adb shell input keyevent <code>` | Полный путь через систему | Медленный (~100мс на событие), нет точного контроля таймингов |
| **Instrumentation** | `Instrumentation.sendKeySync(KeyEvent)` | Контроль meta state, timestamp | Требует instrumentation в процессе IME |
| **UiAutomation** | `UiAutomation.injectInputEvent()` | Работает из тестового процесса | Нужен API 18+, не все KeyEvent попадают в IME |
| **Прямой вызов** | `K12KbIME.onKeyDown(keyCode, new KeyEvent(...))` | Полный контроль, быстро | Требует доступ к инстансу IME, нет системной обработки |

**Рекомендация:** Комбинированный подход:
- **Для интеграционных тестов:** `Instrumentation.sendKeySync()` — полный путь через систему
- **Для unit-тестов стейт-машины:** Прямой вызов `onKeyDown`/`onKeyUp` с кастомными KeyEvent (контроль `eventTime` для таймингов)

**Пример стаба KeyEvent для двойного нажатия:**
```java
// Создаём KeyEvent с контролируемым временем
long t0 = SystemClock.uptimeMillis();
KeyEvent down1 = new KeyEvent(t0, t0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0);
KeyEvent up1 = new KeyEvent(t0, t0 + 50, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0);

// Пауза меньше time-double-press
long t1 = t0 + 100; // 100мс между нажатиями
KeyEvent down2 = new KeyEvent(t1, t1, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0);
KeyEvent up2 = new KeyEvent(t1, t1 + 50, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0);

// Инъекция через Instrumentation
instrumentation.sendKeySync(down1);
instrumentation.sendKeySync(up1);
Thread.sleep(100);
instrumentation.sendKeySync(down2);
instrumentation.sendKeySync(up2);
```

### 2.2 Жесты — MotionEvent

**Текущая архитектура:**
```
Сенсор клавиатуры MotionEvent
  → K12KbIME.onGenericMotionEvent(MotionEvent)
    → InputMethodServiceCoreGesture.PerformAtomicGestureAction()
      → Вычисление delta X/Y
      → motion_delta_min = (GESTURE_MOTION_CONST - sensitivity) * Kpower
      → InputConnection.sendKeyEvent(DPAD_LEFT/RIGHT/UP/DOWN)
```

**Стратегия стаббирования:**

`onGenericMotionEvent` вызывается сенсором BB KEY2. Для стаббирования:

```java
// Создаём MotionEvent с нужными координатами
MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[1];
props[0] = new MotionEvent.PointerProperties();
props[0].id = 0;
props[0].toolType = MotionEvent.TOOL_TYPE_FINGER;

MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
coords[0] = new MotionEvent.PointerCoords();
coords[0].x = startX;
coords[0].y = startY;

long downTime = SystemClock.uptimeMillis();
MotionEvent downEvent = MotionEvent.obtain(
    downTime, downTime, MotionEvent.ACTION_DOWN,
    1, props, coords, 0, 0, 1.0f, 1.0f,
    0, 0, InputDevice.SOURCE_TOUCHPAD, 0);

// Серия MOVE событий для имитации свайпа
for (int i = 1; i <= steps; i++) {
    coords[0].x = startX + (endX - startX) * i / steps;
    coords[0].y = startY + (endY - startY) * i / steps;
    long moveTime = downTime + i * 16; // ~60fps
    MotionEvent moveEvent = MotionEvent.obtain(
        downTime, moveTime, MotionEvent.ACTION_MOVE,
        1, props, coords, 0, 0, 1.0f, 1.0f,
        0, 0, InputDevice.SOURCE_TOUCHPAD, 0);
    ime.onGenericMotionEvent(moveEvent);
    moveEvent.recycle();
}
```

**Критически важно:** Источник событий (`InputDevice.SOURCE_TOUCHPAD`) должен соответствовать аппаратному сенсору BB KEY2. Жесты фильтруются по `gesture-row4-begin-y` и `gesture-row1-begin-y` из `keyboard_core.json`.

### 2.3 AccessibilityEvent

**Текущая архитектура:**
```
Системное AccessibilityEvent
  → K12KbAccessibilityService.onAccessibilityEvent(event)
    → Определение типа: TYPE_VIEW_FOCUSED, TYPE_WINDOW_STATE_CHANGED, ...
    → Обновление оверлея, поисковые плагины, цифровая панель
```

**Стратегия стаббирования:**

AccessibilityEvent нельзя создать напрямую (конструктор пакетный). Варианты:

| Метод | Реализация |
|-------|------------|
| **Рефлексия** | `AccessibilityEvent.obtain(type)` — создаёт пустое событие, заполняем поля |
| **Мок AccessibilityNodeInfo** | Через Mockito/PowerMock мокать `event.getSource()` |
| **Реальные события** | Запустить UiAutomator тест, навигировать по UI — события генерируются системой |

**Рекомендация:** Для unit-тестов `SearchClickPlugin` и оверлеев — мок через `AccessibilityEvent.obtain()`. Для интеграционных — реальная навигация через UiAutomator.

### 2.4 InputConnection

**Текущая архитектура:**
```
K12KbIME → getCurrentInputConnection()
  → commitText("символ", 1)
  → sendKeyEvent(new KeyEvent(...))
  → getTextBeforeCursor(n, 0)  // для предсказания
  → setComposingText(...)      // для composing
```

**Стратегия стаббирования:**

`InputConnection` — интерфейс, его легко замокать:

```java
InputConnection mockIC = new BaseInputConnection(targetView, true) {
    StringBuilder buffer = new StringBuilder();

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        buffer.append(text);
        return true;
    }

    @Override
    public CharSequence getTextBeforeCursor(int n, int flags) {
        int start = Math.max(0, buffer.length() - n);
        return buffer.substring(start);
    }
};
```

Это критически важно для тестирования предсказания: `WordPredictor` использует `getTextBeforeCursor()` для контекста.

---

## 3. Уровни автотестирования

### 3.1 Уровень 1: Нативные unit-тесты (хост)

**Что уже есть:** `app/src/main/jni/test_symspell.c` — 5 тестовых функций:
- `test_basic` — создание, добавление слов, lookup (exact + typo)
- `test_prefix` — префиксный поиск, сортировка по частоте, original forms
- `test_save_load` — сохранение/загрузка через mmap (v1)
- `test_save_load_v2` — roundtrip с original forms
- `test_bigrams` — биграмный lookup, save/load v3, backward compat

**Что нужно добавить:**
- Тесты `keyboard_distance.c` — проверка весов соседних клавиш (QWERTY: q-w = 0.5, q-z = стандарт)
- Тесты `cdb.c` — создание/чтение CDB, коллизии хэшей
- Нагрузочные тесты — словарь 300K слов, время lookup < 1мс
- Тесты `translation_jni.c` — если есть нативная часть перевода

**Сборка и запуск:**
```bash
# Компиляция под хост (x86_64)
gcc -o test_symspell test_symspell.c symspell.c keyboard_distance.c cdb.c -lm
./test_symspell app/src/main/assets/dictionaries/en_base.txt
```

### 3.2 Уровень 2: Java unit-тесты (JVM, без Android)

**Тестируемые классы (без зависимости от Android SDK):**
- `KeyboardLayout` — парсинг JSON раскладок, маппинг клавиш
- `KeyboardLayoutManager.KeyToCharCode()` — маппинг keyCode + модификаторы → символ
- `K12KbSettings` — чтение/запись настроек (нужен мок SharedPreferences)
- `FileJsonUtils` — загрузка и JS-патчинг JSON (нужен мок Context для assets)

**Фреймворк:** JUnit 4 + Mockito (без Robolectric, т.к. нет AndroidX)

**Пример теста маппинга клавиш:**
```java
@Test
public void testKeyToCharCode_EnglishLayout() {
    // Загрузить keyboard_layouts.json из тестовых ресурсов
    KeyboardLayoutManager mgr = loadTestLayout("en_us");

    // Без модификаторов: A → 'a'
    assertEquals('a', mgr.KeyToCharCode(KeyEvent.KEYCODE_A, 0));

    // Shift: A → 'A'
    assertEquals('A', mgr.KeyToCharCode(KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_ON));

    // Alt mode: A → альтернативный символ по раскладке
    char altChar = mgr.KeyToCharCode(KeyEvent.KEYCODE_A, META_ALT_CUSTOM);
    assertNotEquals(0, altChar);
}
```

### 3.3 Уровень 3: Instrumentation-тесты (на устройстве)

**Фреймворк:** Android Instrumentation Test + UiAutomator 2

**Архитектура тестов:**

```
TestRunner (тестовый APK)
  ├── IME unit tests (в процессе IME через ServiceTestRule)
  │   ├── Стейт-машина клавиш (KeyEvent injection)
  │   ├── Жесты (MotionEvent injection)
  │   └── Предсказание (InputConnection mock)
  │
  └── Integration tests (через UiAutomator)
      ├── Набор текста в EditText
      ├── Переключение языков
      ├── Проверка SuggestionBar
      └── Настройки приложения
```

**Ключевые тестовые сценарии:**

#### A. Тест стейт-машины клавиш
```java
@Test
public void testDoublePressProducesDoubleVariant() {
    // Получить инстанс IME через ServiceTestRule
    K12KbIME ime = getIMEInstance();

    // Загрузить keyboard_core.json для таймингов
    int doublePressTime = 300; // из keyboard_core.json

    // Инъекция двойного нажатия с правильным таймингом
    long t0 = SystemClock.uptimeMillis();
    ime.onKeyDown(KEYCODE_A, makeKeyEvent(ACTION_DOWN, KEYCODE_A, t0));
    ime.onKeyUp(KEYCODE_A, makeKeyEvent(ACTION_UP, KEYCODE_A, t0 + 50));

    long t1 = t0 + doublePressTime / 2; // внутри окна двойного нажатия
    ime.onKeyDown(KEYCODE_A, makeKeyEvent(ACTION_DOWN, KEYCODE_A, t1));
    ime.onKeyUp(KEYCODE_A, makeKeyEvent(ACTION_UP, KEYCODE_A, t1 + 50));

    // Дождаться обработки (стейт-машина использует Handler.postDelayed)
    Thread.sleep(doublePressTime + 100);

    // Проверить что сработал DoublePress вариант
    String committed = mockInputConnection.getCommittedText();
    // Ожидаем символ из KeyVariants.DoublePress для KEYCODE_A
    assertNotEquals("a", committed);
}
```

#### B. Тест предсказания через InputConnection
```java
@Test
public void testPredictionAfterTyping() {
    K12KbIME ime = getIMEInstance();
    MockInputConnection mockIC = new MockInputConnection();
    // ... привязать mockIC к IME

    // Набрать "hel"
    typeString(ime, "hel");

    // Дождаться асинхронной загрузки предсказаний
    waitForPredictions(5000);

    // Проверить SuggestionBar
    SuggestionBar bar = ime.getSuggestionBar();
    List<String> suggestions = bar.getCurrentSuggestions();

    assertTrue(suggestions.contains("hello"));
    assertTrue(suggestions.contains("help"));
}
```

#### C. Тест жестового управления курсором
```java
@Test
public void testHorizontalGestureMoveCursor() {
    K12KbIME ime = getIMEInstance();
    MockInputConnection mockIC = new MockInputConnection("hello world", 5); // курсор на 5

    // Имитация свайпа вправо (10 пикселей)
    MotionEvent down = makeMotionEvent(ACTION_DOWN, 100, 200, SOURCE_TOUCHPAD);
    MotionEvent move = makeMotionEvent(ACTION_MOVE, 110, 200, SOURCE_TOUCHPAD);
    MotionEvent up = makeMotionEvent(ACTION_UP, 110, 200, SOURCE_TOUCHPAD);

    ime.onGenericMotionEvent(down);
    ime.onGenericMotionEvent(move);
    ime.onGenericMotionEvent(up);

    // Проверить что курсор сдвинулся вправо
    // (IME отправляет DPAD_RIGHT через sendKeyEvent)
    assertTrue(mockIC.receivedKeyEvent(KEYCODE_DPAD_RIGHT));
}
```

### 3.4 Уровень 4: E2E-тесты (UiAutomator)

Полная интеграция через UI:

```java
@Test
public void testTypeAndAcceptSuggestion() {
    UiDevice device = UiDevice.getInstance(getInstrumentation());

    // Открыть тестовое приложение с EditText
    // (или использовать ActivityKeyboardTest)
    device.executeShellCommand("am start -n com.ai10.k12kb/.ActivityKeyboardTest");

    // Набрать через shell input
    device.executeShellCommand("input text helo");

    // Найти SuggestionBar и тапнуть "hello"
    UiObject suggestion = device.findObject(new UiSelector()
        .text("hello")
        .className("android.widget.TextView"));
    if (suggestion.exists()) {
        suggestion.click();
    }

    // Проверить текст в EditText
    UiObject input = device.findObject(new UiSelector()
        .resourceId("com.ai10.k12kb:id/input"));
    assertEquals("hello ", input.getText());
}
```

---

## 4. Существующая тестовая инфраструктура

### 4.1 ActivityKeyboardTest

Файл: `app/src/main/java/com/ai10/k12kb/ActivityKeyboardTest.java`

Уже реализованная debug-активити с:
- `View.OnKeyListener` — логирование KeyEvent (keyCode, metaState в бинарном виде)
- `View.OnGenericMotionListener` — логирование MotionEvent (X, Y, action)
- `IDebugUpdate` callback — вывод дебаг-текста из IME
- Отображение `FileJsonUtils.CustomizationLoadVariants` — какие конфиги загружены
- Информация об устройстве (Build.BOARD, PRODUCT, DEVICE, DISPLAY, BRAND)
- Чекбокс переключения в режим keyboard view test

**Рекомендация:** Использовать `ActivityKeyboardTest` как хост-активити для instrumentation-тестов. Она уже настроена для перехвата и логирования всех типов событий.

### 4.2 test_symspell.c

Файл: `app/src/main/jni/test_symspell.c`

Полноценный нативный тест-харнес с:
- Макросами ASSERT_EQ / ASSERT_TRUE
- 5 тестовыми функциями (basic, prefix, save/load v2, save/load, bigrams)
- Бенчмарком с реальным словарём (10K lookups, измерение мкс/lookup)
- Подсчётом passed/failed

**Статус:** Готов к использованию, запускается на хосте (x86_64).

### 4.3 JS-патчи для тестирования

Файлы в `app/src/main/assets/js_patches/`:
- `k12kb_as_options.test.ctrl_q.js` — тест кастомного действия Ctrl+Q
- `keyboard_mechanics.test.letter_exception_action.js` — тест исключений для букв
- `plugin_data.test.del_plugin.js` — тест удаления плагина

Эти файлы демонстрируют паттерн для тестирования JS-патчинга через Rhino.

---

## 5. Ограничения и обходные пути

### 5.1 Проблема доступа к инстансу IME

InputMethodService создаётся системой. Для получения инстанса из теста:

**Вариант A — Статическая ссылка (уже реализовано):**
```java
// В K12KbIME уже есть:
public static boolean IS_KEYBOARD_TEST = false;
public static IDebugUpdate DEBUG_UPDATE = null;
public static String DEBUG_TEXT = "";
```
Можно добавить `public static K12KbIME INSTANCE;` в `onCreate()`.

**Вариант B — Bound service через Instrumentation:**
Нужно расширить IME для поддержки `onBind()` с тестовым IBinder.

### 5.2 Проблема таймингов стейт-машины

Стейт-машина использует `Handler.postDelayed()` для определения short/double/long press. В тестах:
- Использовать `SystemClock.setCurrentTimeMillis()` (требует рут)
- Или дожидаться реальных таймаутов в тестах (медленнее, но надёжнее)
- Или внедрить тестовый `Clock` через DI (требует рефакторинг)

**Рекомендация:** Для первой итерации — реальные таймауты (`Thread.sleep()`), позже рефакторить на `Clock` интерфейс.

### 5.3 Проблема Android Support Library (pre-AndroidX)

Проект использует `com.android.support:appcompat-v7:25.4.0` (до AndroidX). Это ограничивает:
- Нельзя использовать `androidx.test` напрямую
- `ServiceTestRule` из Support Library ограничен
- Instrumentation runner: `android.test.InstrumentationTestRunner` вместо `androidx.test.runner.AndroidJUnitRunner`

### 5.4 Ограничение lambda/streams

Весь код должен быть Java 7/8 **без lambda и method references** (BootstrapMethodError на Android 8.x dex). Тестовый код подчиняется тому же ограничению если компилируется тем же build pipeline.

---

## 6. Рекомендуемый план внедрения

### Фаза 1: Нативные тесты (0 зависимостей, сразу запускаемо)

1. Расширить `test_symspell.c`:
   - Добавить тесты `keyboard_distance.c` (QWERTY + ЙЦУКЕН раскладки)
   - Добавить тесты CDB-кэша (создание, чтение, коллизии)
   - Добавить тест на словарь 300K слов (время < 5мс)
2. Добавить в `tools/build_ci.sh` шаг компиляции и запуска нативных тестов
3. **Трудозатраты:** ~1-2 дня

### Фаза 2: Юнит-тесты на JVM

1. Добавить зависимость JUnit 4 + Mockito в build pipeline (или отдельный Makefile)
2. Написать тесты:
   - `KeyboardLayoutManagerTest` — маппинг клавиш для EN/RU раскладок
   - `K12KbSettingsTest` — чтение/запись всех 40+ настроек с мок SharedPreferences
   - `FileJsonUtilsTest` — загрузка JSON из ассетов + JS-патчинг
3. **Трудозатраты:** ~3-5 дней

### Фаза 3: Instrumentation-тесты на устройстве

1. Создать тестовый APK модуль (app/src/androidTest/)
2. Реализовать:
   - `KeyStateMachineTest` — все комбинации short/double/triple/long press
   - `PredictionIntegrationTest` — набор текста → проверка предложений
   - `GestureTest` — инъекция MotionEvent → проверка перемещения курсора
   - `LanguageSwitchTest` — переключение раскладок
3. Использовать `ActivityKeyboardTest` как хост
4. **Трудозатраты:** ~5-8 дней

### Фаза 4: E2E и регрессия

1. UiAutomator-тесты для полного пользовательского сценария
2. CI-интеграция с подключённым устройством (или эмулятором API 26)
3. Регрессионный набор для каждого PR
4. **Трудозатраты:** ~3-5 дней

---

## 7. Ссылки на документацию

- Руководство пользователя (EN): https://k12kb.gitbook.io/doc/en/manual
- Руководство пользователя (RU): https://k12kb.gitbook.io/doc/ru/manual
- Заметки о выпусках: `doc/release_2_4.txt` — `doc/release_2_7.txt`
- JSON-схемы конфигураций: `doc/schema/*.schema.json`
  - `keyboard_core.schema.json` — тайминги: time-short-press, time-double-press, time-triple-press, time-long-press, gesture-motion-base-sensitivity и др.
  - `keyboard_layouts.schema.json` — формат раскладок
  - `keyboard_mechanics.schema.json` — формат механик (действия на нажатия)
  - `k12kb_as_options.schema.json` — конфиг Accessibility Service
  - `plugin_data.schema.json` — конфиг поисковых плагинов

---

## 8. Матрица покрытия: тест-план → автотесты

| Тест-план ID | Область | Авто-уровень | Стратегия стаббирования |
|-------------|---------|-------------|------------------------|
| TP-001 | Жизненный цикл IME | Instrumentation | ServiceTestRule + lifecycle callbacks |
| TP-002 | Базовый ввод | Instrumentation | `Instrumentation.sendKeySync()` |
| TP-003 | Стейт-машина | Instrumentation | Прямой вызов `onKeyDown`/`onKeyUp` с контролируемыми KeyEvent |
| TP-004 | Предсказание | Instrumentation + Native | Мок `InputConnection` + `test_symspell.c` |
| TP-005 | Языки | E2E (UiAutomator) | `input keyevent` + проверка UI |
| TP-006 | Жесты | Instrumentation | Кастомные MotionEvent с `SOURCE_TOUCHPAD` |
| TP-007 | OSK | E2E (UiAutomator) | `UiDevice.click()` на координатах клавиш |
| TP-008 | Настройки | JVM unit + E2E | Мок SharedPreferences + UiAutomator для UI |
| TP-009 | Перевод | Instrumentation | Мок `InputConnection` + проверка SuggestionBar |
| TP-010 | Accessibility | Instrumentation | `AccessibilityEvent.obtain()` + мок NodeInfo |
| TP-011 | Плагины | JVM unit | Мок AccessibilityService контекста |
| TP-012 | JS-патчинг | JVM unit | Тестовые JSON + JS файлы из assets |
| TP-013 | Уведомления | E2E | `UiDevice.openNotification()` + проверка |
| TP-014 | Устройство-раскладки | JVM unit | Мок `Build.DEVICE` / `Build.PRODUCT` |
| TP-015 | Нативный код | Native (хост) | `test_symspell.c` + новые тесты |
| TP-016 | Граничные случаи | Все уровни | По специфике каждого кейса |
| TP-017 | Сборка | CI скрипт | `tools/build_apk.sh` + `tools/build_ci.sh` |

---

## 9. Итоги

**Текущее состояние:** Проект имеет нативные unit-тесты (`test_symspell.c`), debug-активити (`ActivityKeyboardTest`) и тестовые JS-патчи. Это хорошая база для расширения.

**Ключевая рекомендация:** Начать с Фазы 1 (нативные тесты — нулевые зависимости, максимальная отдача) и Фазы 2 (JVM unit-тесты — быстрый feedback loop). Instrumentation-тесты на устройстве (Фаза 3) реализовать после стабилизации API инстанса IME (добавление `INSTANCE` статической ссылки).

**Критический фактор:** IME-специфика требует особого подхода к стаббированию. Стандартные Android-тестовые инструменты (Espresso, Robolectric) плохо подходят для InputMethodService. Комбинация прямых вызовов методов IME + мок InputConnection + нативных тестов даёт наибольшее покрытие при минимальных затратах.
