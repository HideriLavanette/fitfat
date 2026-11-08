# FitFat — правила для Claude Code

Трекер веса и КБЖУ для Android. Автор — Эльдияр (GitHub: HideriLavanette/FitFat).
Все продуктовые и дизайнерские решения — в `docs/DESIGN.md`. Прочитай его перед любой задачей по интерфейсу.

## Стек
- Kotlin Multiplatform + Compose Multiplatform (Android и iOS из одного модуля `app`), Material 3 (тема из `ui/theme/Theme.kt`), Room KMP + KSP, Navigation и Lifecycle от JetBrains, Ktor, kotlinx.datetime. Как устроено и что ещё не работает на iOS — `docs/IOS.md`.
- Код интерфейса и логики — в `app/src/commonMain`; платформенное — `expect`/`actual` в `androidMain` (`*.android.kt`) и `iosMain` (`*.ios.kt`). В `commonMain` нельзя: `java.*`, `String.format`, `Dispatchers.IO` (есть `ioDispatcher`), `Math.*` (есть `app.fitfat.time.Math`), `LocalContext`. Даты — kotlinx.datetime с расширениями из `app.fitfat.time`.
- AGP 8.10.1, Kotlin 2.2.21, Compose Multiplatform 1.9.3, Gradle 8.11.1, JDK 17 в CI. **Не обновлять** без отдельной просьбы. Если обновляешь — синхронно поменяй `gradle-version` в `.github/workflows/*.yml`.
- minSdk 26, targetSdk 35. ID приложения `app.fitfat` (и bundle id на iOS) — менять нельзя никогда.

## Обязательно
- Перед словом «готово» собери проект: `./gradlew assembleDebug` без ошибок. Трогал `commonMain` или `iosMain` — ещё и `./gradlew :app:compileKotlinIosSimulatorArm64` (на Windows это компилирует iOS-код без линковки).
- Room: при изменении сущностей повышай версию БД и пиши `Migration`. Данные пользователя терять нельзя, `fallbackToDestructiveMigration` запрещён.
- Не называй функции `setX`, если есть свойство `x` с публичным или приватным сеттером — конфликт JVM-сигнатур.
- Цвета только из `MaterialTheme.colorScheme` и `MacroColors`. Никаких случайных HEX в экранах.
- Тексты интерфейса — на русском, с ёлочками «», тире —, неразрывным пробелом в числах (1 820).
- Каждое заметное изменение — строка в `CHANGELOG.md` в разделе следующей версии `## [x.y.z]`.
- Ключ подписи, пароли и секреты не трогать и не создавать.
- Работай небольшими шагами: один экран или одна функция за задачу, коммит после зелёной сборки.

## Релизы
- Пуш в `main` → CI собирает debug-APK и iOS-приложение для симулятора (job `ios`, macOS). Тег `vX.Y.Z` → workflow Release собирает подписанный APK и публикует GitHub Release; приложение само предлагает обновление. iOS в релиз не попадает, пока нет аккаунта Apple Developer (`docs/IOS.md`).
- Тег с дефисом (`v0.4.0-beta1`) — бета-канал.
- Тег ставит человек после зелёного CI. Сам теги не создавай и не пушь без просьбы.
