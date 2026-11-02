# 🛡️ CallGuard — Privacy-First Android Anti-Spam

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2+-7F52FF.svg?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android SDK](https://img.shields.io/badge/Min%20SDK-29%20%28Android%2010%2B%29-3DDC84.svg?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-4285F4.svg?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Offline--First-FF6F00.svg?style=flat)](#architecture)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**CallGuard** — это современное, высокопроизводительное приложение для автоматической блокировки спам-вызовов с бескомпромиссным акцентом на **конфиденциальность пользователей**. В отличие от большинства Caller ID сервисов, CallGuard **не загружает** вашу записную книжку на внешние серверы и работает по принципу **100% Offline-First**.

---

## ✨ Ключевые возможности

- 🛡️ **Многоуровневый фильтр звонков:** Автоматическое отклонение мошенников и нежелательных вызовов через системный `CallScreeningService`.
- ⚡ **Режим «Жду курьера» (Courier Mode):** Временный байпас всех фильтров на 30 или 60 минут, чтобы не пропустить важную доставку или такси.
- 🎭 **Умные правила и маски (Smart Rules):** Блокировка вызовов по строковым шаблонам (например, `+998 71 *`), скрытых и иностранных номеров.
- ⚠️ **Эвристический анализ & Overlay:** Вывод неинвазивного всплывающего предупреждения (`SYSTEM_ALERT_WINDOW`) при сомнительных номерах (Spam Score 30–79).
- 📦 **Офлайн Импорт/Экспорт:** Обмен локальными базами спама через JSON-файлы (версии v1/v2) с транзакционной валидацией и ограничением объема (до 5 МБ).
- ☁️ **Privacy-First Hybrid Sync:** Фоновая синхронизация глобальной базы спама через `WorkManager` без раскрытия личных данных пользователя.
- 🕒 **Time-To-Live (TTL) очистка:** Автоматическое удаление устаревших глобальных записей (>14 дней) для сохранения высокой скорости БД.
- 🔒 **Защита «Proof of Call»:** Возможность отправки жалоб только на номера, реально звонившие устройству в течение 24 часов.
- 🌐 **Полная локализация (i18n):** Поддержка 3 языков — Узбекский (UZ), Русский (RU) и Английский (EN).
- 🎨 **Material 3 UI:** Современный адаптивный интерфейс с поддержкой динамических цветов, системной Тёмной/Светлой темы и наглядным дашбордом статистики.

---

## 🏗️ Архитектура и Пайплайн Фильтрации

Приложение написано с соблюдением принципов **Clean Architecture** и паттерна **MVVM**. Все операции с данными полностью реактивны и основаны на `Kotlin Coroutines` и `StateFlow`.

### Иерархия обработки входящего вызова (`CallScreeningServiceImpl`):

Во время входящего звонка приложение принимает решение за единицы миллисекунд по строгому алгоритму:

```text
[Входящий вызов]
       │
       ▼
 1. Белый список контактов? ───────► (Да) ──► [ Пропустить звонок ]
       │ (Нет)
       ▼
 2. Активен "Режим курьера"? ─────► (Да) ──► [ Пропустить звонок ]
       │ (Нет)
       ▼
 3. Проверка БД (Room):
    ├── Локальная запись спама ────────────► [ ОТКЛОНИТЬ звонок ]
    ├── Глобальный спам (Score >= 80) ────► [ ОТКЛОНИТЬ + Тихий Push ]
    └── Подозрительный (Score 30..79) ────► [ Пропустить + Overlay ⚠️ ]
       │ (Не найден)
       ▼
 4. Проверка Smart Rules / Эвристики ─────► [ Блокировка / Оверлей / Пропуск ]
```
🛠️ Технологический стек
Язык: Kotlin 2.2+
UI Framework: Jetpack Compose (Material 3 components, Navigation)
Database: Room Database v5 (c поддержкой автоматических и кастомных миграций)
Key-Value Storage: Jetpack DataStore (Preferences)
Background Work: WorkManager (PeriodicWorkRequest, CoroutineWorker)
System Integration: CallScreeningService, WindowManager (Application Overlay), RoleManager (API 29+)
Async & Reactive: Kotlin Coroutines, StateFlow, SharedFlowJSON Serialization: Gson / kotlinx.serialization
Testing: JUnit 4, Espresso, AndroidX Test libraries (40/40 Unit-тестов)

🔒 Разрешения и Безопасность
CallGuard запрашивает минимально необходимый набор системных разрешений:
Разрешение — Назначение:
ROLE_CALL_SCREENING — Перехват и анализ номеров входящих вызовов на уровне ОС.
READ_CONTACTS — Исключение номеров из телефонной книги из-под действия фильтров.
SYSTEM_ALERT_WINDOW — Отображение плашки предупреждения поверх экрана звонилки.
POST_NOTIFICATIONS — Информирование пользователя о заблокированных спам-вызовах (без звука).
Privacy Guarantee: Приложение не имеет доступа к передаче личных контактов или истории звонков за пределы устройства.
🚀 Сборка и Запуск Проекта
Требования к среде:
Android Studio: Ladybug / Jellyfish или новее
JDK: 21
Android SDK: Target API 35+ / Min API 29 (Android 10)
Шаги по сборке:
Склонируйте репозиторий:
git clone [https://github.com/your-username/CallGuard.git](https://github.com/your-username/CallGuard.git)
cd CallGuard
Запустите модульные тесты:
./gradlew test
Скомпилируйте Debug APK:
./gradlew assembleDebug
Готовый APK файл появится по пути: app/build/outputs/apk/debug/app-debug.apk.
📄 ЛицензияПроект распространяется под лицензией MIT. Подробнее см. в файле LICENSE.
