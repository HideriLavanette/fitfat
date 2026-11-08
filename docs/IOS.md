# FitFat на iOS — как устроено и что осталось

Решение автора (09.11.2026): iOS-версия делается на **Kotlin Multiplatform + Compose Multiplatform**, а не отдельным
приложением на Swift. Один код интерфейса и логики для Android и iOS; платформенное — через `expect`/`actual`.
У автора нет Mac и аккаунта Apple Developer: iOS собирается только в GitHub Actions на macOS-раннере.

## Структура
- Модуль один — `app`. Это и Android-приложение (`com.android.application`, id `app.fitfat` не меняется),
  и Kotlin Multiplatform-модуль с целями `androidTarget`, `iosArm64`, `iosSimulatorArm64`.
- `app/src/commonMain` — всё общее: данные (Room KMP), логика, тема, компоненты, экраны, `AppRoot`.
- `app/src/androidMain` — `FitfatApp`, `MainActivity`, манифест, ресурсы и `actual`-реализации для Android
  (вибрация и звук, клавиатура, сканер штрихкодов Google, вход Google, уведомления через `AlarmManager`,
  фото из галереи и камеры, самообновление из GitHub Releases).
- `app/src/iosMain` — `MainViewController()` для Swift и `actual`-реализации для iOS.
- `iosApp/` — тонкая обёртка Xcode: `project.yml` (XcodeGen), `iosApp/iOSApp.swift`, `Info.plist`, иконка.
  Сам проект `.xcodeproj` не хранится — CI генерирует его XcodeGen'ом.
- Платформенные значения сборки (адрес ассистента, client id Google, версия) — в `BuildValues`, файл генерируется
  Gradle-задачей из `gradle.properties` / `-P` для обеих платформ (на Android `BuildConfig` больше не нужен).

## Замены Android-only API
| Было (Android) | Стало (common) |
|---|---|
| `java.time.*` | `kotlinx.datetime` + расширения в `app.fitfat.time` с привычными именами (`plusDays`, `toEpochDay`, `isAfter`, `YearMonth`…) и русский форматтер дат |
| `SharedPreferences` | `expect class Prefs(name)` — на Android те же `SharedPreferences` с теми же именами файлов и ключей (данные сохраняются), на iOS `NSUserDefaults` |
| `String.format(Locale("ru"), …)` | `app.fitfat.time.Fmt` (`f1`, `hhmm`, разряды с неразрывным пробелом) |
| `org.json`, `HttpURLConnection` | `kotlinx.serialization.json`, Ktor (OkHttp на Android, Darwin на iOS) |
| `AndroidViewModel(app)` | `ViewModel` + `AppGraph` (база, репозитории, настройки) — создаётся платформой при старте |
| `LocalContext`, `FitfatApp` в экранах | `LocalAppGraph` / `AppGraph.get()` |
| `BackHandler` из `androidx.activity` | `androidx.compose.ui.backhandler.BackHandler` (Compose Multiplatform) |
| Room `Migration(SupportSQLiteDatabase)` | Room KMP: `Migration(SQLiteConnection)`, драйвер `BundledSQLiteDriver` на обеих платформах; файл `fitfat.db` прежний |

## Чего на iOS пока нет (первая версия)
Флаги в `Platform` прячут соответствующие кнопки, чтобы не оставлять кнопок без действия.
- **Вход через Google** и, как следствие, **ассистент** — нужен SDK Google Sign-In или OAuth через `ASWebAuthenticationSession`.
- **Сканер штрихкодов** — на Android это ML Kit; на iOS нужен `AVCaptureSession`.
- **Фото продуктов** с камеры и из галереи — нужен `PHPickerViewController`; уже сохранённые фото показываются.
- **Самообновление** — на iOS его нет по правилам платформы: обновления через TestFlight / App Store.
- **Цвета системы** (Material You) — только Android 12+.
- Напоминания на iOS — локальные уведомления `UNUserNotificationCenter`; проверка «уже взвесился — не напоминать»
  невозможна в момент срабатывания, напоминание приходит всегда.

## Сборка и проверка
- Локально (Windows): `./gradlew assembleDebug` собирает Android и компилирует весь `commonMain`.
  Цели iOS на Windows отключены (`kotlin.native.ignoreDisabledTargets=true`), `iosMain` локально не компилируется.
- CI (`.github/workflows/ci.yml`, job `ios`, `macos-latest`): компилирует iOS-фреймворк
  (`:app:linkDebugFrameworkIosSimulatorArm64`), генерирует проект XcodeGen'ом и собирает приложение для симулятора
  без подписи. Это единственная проверка `iosMain`, поэтому любая правка там — через пуш в ветку и зелёный CI.
- Запустить на iPhone без аккаунта Apple Developer нельзя. Когда аккаунт появится: сертификат и профиль в секреты,
  в `release.yml` — шаг `xcodebuild archive` + экспорт IPA и загрузка в TestFlight.

## Порядок миграции (чекбоксы — что уже сделано)
- [x] Модуль `app` → KMP, код в `androidMain`, зелёная сборка Android.
- [x] Общие заглушки: даты, формат, `Prefs`, `BuildValues`, `Platform`.
- [x] `data/` в `commonMain` (Room KMP, Ktor).
- [x] Тема, компоненты, экраны, `AppRoot` в `commonMain`; `expect`/`actual` для платформенного.
- [x] `iosMain`, `iosApp`, job `ios` в CI.
- [ ] Первый зелёный прогон job `ios` в GitHub Actions (нужен пуш в `main`).
- [ ] Аккаунт Apple Developer → подпись, TestFlight, шаг в `release.yml`.
- [ ] Сканер штрихкодов, фото, вход Google на iOS (см. «Чего на iOS пока нет»).

## Как проверять iOS-код без Mac
Kotlin на Windows умеет компилировать iOS-библиотеку без линковки: `./gradlew :app:compileKotlinIosSimulatorArm64`
проверяет `commonMain` и `iosMain` на ошибки (платформенные API iOS из дистрибутива Kotlin/Native). Линковка фреймворка
и сборка приложения — только на macOS, то есть в CI.
