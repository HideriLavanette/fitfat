# Макеты FitFat — эталон интерфейса

Это исходники интерактивных макетов (Design Component, HTML + JS). Они **не запускаются** в приложении
и не являются кодом для копирования. Это точная спецификация: размеры, отступы, радиусы, цвета,
тексты, состояния и поведение.

## Как читать файл макета
- Разметка внутри `<x-dc>…</x-dc>` — внешний вид. Inline-стили дают точные px (1 px макета = 1 dp на телефоне 390×844).
- `{{имя}}` — значение, которое вычисляется в `renderVals()` в блоке `<script type="text/x-dc">` ниже.
- В `renderVals()` и методах класса — логика: жесты, инерция барабанов, расчёты КБЖУ, тексты состояний.
- `<sc-for>` — список, `<sc-if>` — условный показ, `<dc-import name="X">` — экран X как фон (например, под модалкой).
- Цвета в макетах — роли Material 3 тёмной схемы (см. `ui/theme/Theme.kt`): `#191113` surface, `#22191c` surfaceContainerLow,
  `#261d20` surfaceContainer, `#31282a` surfaceContainerHigh, `#3c3235` surfaceContainerHighest, `#ffb0c9` primary,
  `#541d33` onPrimary, `#6f3349` primaryContainer, `#ffd9e3` onPrimaryContainer, `#5a3f48` secondaryContainer,
  `#e2bdc7` secondary, `#efdfe1` onSurface, `#d5c2c6` onSurfaceVariant, `#9e8c90` outline, `#514347` outlineVariant.
  В Compose используй `MaterialTheme.colorScheme.*`, а не HEX.
- `support.js` и рантайм макетов не нужны — их в репозитории нет.

## Правило
Экран в приложении должен совпадать с макетом по структуре, компонентам, текстам и поведению.
Если стандартный компонент Material 3 выглядит иначе — делай свой Composable по макету.
Если что-то нельзя повторить один в один — спроси, прежде чем упрощать.
Решения, которых нет в макетах, — в `docs/DESIGN.md`.

## Экраны
| Файл | Экран |
|---|---|
| `Main.dc.html` | 1.1 · Утренний вес |
| `Weight-history.dc.html` | 1.2 · История веса |
| `Weight-edit.dc.html` | 1.3 · Правка записи веса |
| `Today.dc.html` | 2.1 · Главный |
| `Today-empty.dc.html` | 2.2 · Главный — пустой день |
| `Today-over.dc.html` | 2.3 · Главный — перебор |
| `Entry-edit.dc.html` | 2.4 · Правка записи еды |
| `Add-empty.dc.html` | 3.1 · Ввод — пустой |
| `Add-food.dc.html` | 3.2 · Ввод — распознано |
| `Add-variants.dc.html` | 3.3 · Выбор варианта |
| `Claude-estimate.dc.html` | 3.4 · Новый продукт (шторка) |
| `Dishes.dc.html` | 3.5 · Мои блюда |
| `Products.dc.html` | 4.1 · Все продукты |
| `Product-card.dc.html` | 4.2 · Карточка продукта |
| `Product-edit.dc.html` | 4.3 · Новый продукт |
| `Dish-card.dc.html` | 4.4 · Блюдо |
| `Report.dc.html` | 5.1 · Неделя |
| `Assistant.dc.html` | 5.2 · Ассистент |
| `Goals.dc.html` | 5.3 · Цель и нормы |
| `Onboarding.dc.html` | 6.0 · Настройка целиком (интерактив) |
| `Settings.dc.html` | 5.5 · Настройки приложения |
| `Profile.dc.html` | 5.6 · Профиль |
| `Body.dc.html` | 5.7 · Параметры тела |
| `Training.dc.html` | 5.8 · Тренировки |
| `Onb-1.dc.html` | 6.1 · Шаг 1 — о себе |
| `Onb-2m.dc.html` | 6.2 · Шаг 2 — процент жира, м |
| `Onb-2f.dc.html` | 6.2 · Шаг 2 — процент жира, ж |
| `Onb-3.dc.html` | 6.3 · Шаг 3 — спорт и активность |
| `Onb-4.dc.html` | 6.4 · Шаг 4 — цель |
| `Onb-5m.dc.html` | 6.5 · Итог — нормы, м |
| `Onb-5f.dc.html` | 6.5 · Итог — нормы, ж |
