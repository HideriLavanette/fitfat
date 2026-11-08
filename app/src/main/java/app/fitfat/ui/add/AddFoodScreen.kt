package app.fitfat.ui.add

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitfat.data.Macros
import app.fitfat.data.Product
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmt1
import app.fitfat.ui.common.macroLine
import app.fitfat.ui.components.FitText
import java.time.LocalDate

/**
 * Добавление еды (макеты Add-empty 3.1 и Add-food 3.2). Поля 12 / 16 / 20.
 * Пусто — «Недавние» с переходом к порции; есть продукты — итог «+N ккал / останется»
 * и короткие «Недавние» с «+». Кнопки «штрихкод», «блюдо», «Посмотреть все» ведут на экраны 0.4 и появятся с ними.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AddFoodScreen(vm: AddFoodViewModel, date: LocalDate, onClose: () -> Unit, onAllProducts: () -> Unit = {}, productId: Long = 0,
    pendingProduct: Long = 0, onPendingTaken: () -> Unit = {},
    onDishes: () -> Unit = {}, onCreateDish: (List<Picked>) -> Unit = {},
    /** Штрихкод отсканирован: найти продукт ([onFound] — сразу порция) или завести новый. */
    onBarcode: (code: String, onFound: (Product) -> Unit) -> Unit = { _, _ -> },
    /** Ключ открытия экрана: тот же — значит вернулись, выбранное не сбрасывается. */
    session: String = date.toString(),
) {
    LaunchedEffect(date, session) { vm.start(date, session) }
    val products by vm.products.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val left by vm.leftKcal.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    // Клавиатуру убрали или нажали мимо поля — курсор пропадает, «Что съели» выходит из фокуса
    val clearFocus = androidx.compose.ui.platform.LocalFocusManager.current
    val imeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeWasVisible && !imeVisible) clearFocus.clearFocus()
        imeWasVisible = imeVisible
    }
    // Клавиатура — после разворота кнопки «+», иначе раскладка прыгает прямо во время анимации
    // Клавиатура — почти сразу: её въезд прячется под размытием разворота
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(120); focus.requestFocus() }
    val recognized = remember(vm.text, products) { vm.recognized() }

    var focused by remember { mutableStateOf(false) }
    // Продукт выбран, но вес не написан — окно порции (как правка продукта)
    var pending by remember { mutableStateOf<Pair<Recognized, Product>?>(null) }
    var editing by remember { mutableStateOf<Picked?>(null) }
    var portion by remember { mutableStateOf<FrequentItem?>(null) }
    var variants by remember { mutableStateOf<Recognized?>(null) }
    var creating by remember { mutableStateOf<Recognized?>(null) }
    var whenOpen by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    // Пришёл ответ ассистента — клавиатуру прячем и докручиваем до карточки, иначе её не видно
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val bringCard = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(vm.assistant) {
        if (vm.assistant != null) {
            focusManager.clearFocus(); keyboard?.hide()
            kotlinx.coroutines.delay(400) // клавиатура уехала, карточка развернулась
            bringCard.bringIntoView()
        }
    }
    // Фрагмент, для которого выбирали порцию, исчез из текста — закрываем выбор

    // Открыли из карточки продукта — сразу окно порции этого продукта
    var preselected by remember { mutableStateOf(false) }
    LaunchedEffect(productId, products) {
        if (productId > 0 && !preselected) products.firstOrNull { it.id == productId }?.let { p ->
            preselected = true
            portion = FrequentItem(p, p.defaultGrams, if (p.perPiece) p.defaultGrams.coerceAtLeast(1.0) else null)
        }
    }

    // Вернулись сюда из «Всех продуктов» / карточки с «В дневник» — окно порции этого продукта
    LaunchedEffect(pendingProduct, products) {
        if (pendingProduct > 0) products.firstOrNull { it.id == pendingProduct }?.let { p ->
            onPendingTaken()
            portion = FrequentItem(p, p.defaultGrams, if (p.perPiece) p.defaultGrams.coerceAtLeast(1.0) else null)
        }
    }

    fun accept(r: Recognized, p: Product) {
        // Количество написано (граммы, штуки или «14 у») — сразу в список
        val (g, n) = app.fitfat.data.Recognizer.portionFor(r.fragment, p)
        if (g != null || n != null) vm.confirm(r, p, g, n)
        else pending = r to p
    }

    val colors = MaterialTheme.colorScheme
    val hasItems = vm.picked.isNotEmpty()
    val unknown = recognized.firstOrNull { it.candidates.isEmpty() && it.fragment.query.isNotBlank() }
    val modal = pending != null || editing != null || portion != null || variants != null || creating != null || whenOpen || consent

    Column(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { clearFocus.clearFocus() }) }
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (hasItems) 12.dp else 14.dp),
    ) {
        // Шапка: закрыть · «Добавить» · чип «Сейчас · Обед»
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(onClick = onClose, shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(MockIcons.Close, "Закрыть", Modifier.size(24.dp), tint = colors.onSurface) }
            }
            FitText("Добавить", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Surface(onClick = { whenOpen = true }, shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh, modifier = Modifier.height(40.dp)) {
                Row(Modifier.padding(start = 10.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(MockIcons.Clock, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                    Text(whenLabel(vm.date, vm.time, vm.meal, vm.timeTouched), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Одна прокрутка на всё между шапкой и кнопкой «Добавить N»: «Что съели» растёт вместе со списком и не наезжает
        // на итог и «Недавние». Колонка выходит в поля экрана, чтобы увеличенная в фокусе карточка и её тень не обрезались.
        // Экран начали листать пальцем — подсказка над словом прячется до следующего нажатия на текст
        val scroll = rememberScrollState()
        var dismissKey by remember { mutableStateOf(0) }
        LaunchedEffect(scroll) {
            scroll.interactionSource.interactions.collect { if (it is androidx.compose.foundation.interaction.DragInteraction.Start) dismissKey++ }
        }
        val actx = androidx.compose.ui.platform.LocalContext.current
        Column(
            Modifier.weight(1f).bleed(16.dp).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (hasItems) 12.dp else 14.dp),
        ) {
        Composer(focused, hasItems, onTap = { focus.requestFocus() }) {
            // Смахнуть влево — убрать из «Что съели»
            vm.picked.forEachIndexed { i, item ->
                androidx.compose.runtime.key(item.key) {
                    app.fitfat.ui.components.SwipeRemove(
                        onRemove = { vm.remove(item) }, hintKey = app.fitfat.data.Hints.SWIPE_ADD,
                        nudge = app.fitfat.ui.components.nudgeWanted(app.fitfat.data.Hints.SWIPE_ADD, i == 0),
                    ) { PickedRow(item) { editing = item } }
                }
            }
            RecognizeField(
                text = vm.text, onText = vm::onText, recognized = recognized,
                onAccept = ::accept,
                onVariants = { variants = it },
                onFocus = { focused = it },
                popupsEnabled = !modal,
                placeholder = !hasItems,
                // Ассистент — кнопкой в той же подсказке над словом, слева от продукта из базы
                onAssistant = if (app.fitfat.data.Assistant.available) ({ _ ->
                    if (app.fitfat.data.Assistant.consented(actx)) vm.askAssistant(actx) else consent = true
                }) else null,
                assistantBusy = vm.assistantBusy,
                wantsAssistant = { r -> wantsAssistant(r, vm.text) },
                dismissKey = dismissKey,
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    .then(if (hasItems) Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 2.dp) else Modifier)
                    .heightIn(min = if (hasItems) 28.dp else 72.dp),
            )
        }

        // «Добавить блюдо» · «Создать блюдо (N)» из того, что уже выбрано (макет Add-food)
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Сканер штрихкода (макет Add-food: маленькая кнопка 48 слева)
            Surface(
                onClick = { app.fitfat.ui.components.scanBarcode(ctx) { code -> onBarcode(code) { p -> portion = FrequentItem(p, p.defaultGrams, if (p.perPiece) p.defaultGrams.coerceAtLeast(1.0) else null) } } },
                shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer, modifier = Modifier.size(48.dp),
            ) { Box(contentAlignment = Alignment.Center) { Icon(MockIcons.Barcode, "Сканировать штрихкод", Modifier.size(22.dp), tint = colors.onSurfaceVariant) } }
            // Блюдо собирается из продуктов, уже выбранных блюд в нём нет
            val forDish = vm.picked.filter { !it.product.isDish }
            val both = forDish.isNotEmpty()
            Surface(onClick = onDishes, shape = RoundedCornerShape(16.dp), color = if (hasItems) colors.surfaceContainerHighest else colors.surfaceContainer, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    // Рядом с «Создать блюдо» места мало — иконку убираем, чтобы надпись влезала целиком
                    androidx.compose.animation.AnimatedVisibility(!both) { Icon(MockIcons.Dish, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant) }
                    FitText("Добавить блюдо", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, minScale = 0.75f)
                }
            }
            androidx.compose.animation.AnimatedVisibility(both, Modifier.weight(1.25f)) {
                Surface(onClick = { onCreateDish(forDish) }, shape = RoundedCornerShape(24.dp), color = colors.secondaryContainer, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Row(Modifier.padding(start = 10.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        Icon(MockIcons.PlusBold, null, Modifier.size(18.dp), tint = colors.onSecondaryContainer)
                        FitText("Создать блюдо", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSecondaryContainer, minScale = 0.75f, modifier = Modifier.weight(1f, fill = false))
                        // Число строго по центру кружка: без внутренних отступов шрифта
                        Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(colors.primary), contentAlignment = Alignment.Center) {
                            Text(
                                "${forDish.size}", color = colors.onPrimary,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold,
                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                        androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                        androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(if (hasItems) 12.dp else 14.dp)) {
            unknown?.let { u -> UnknownBanner(u.fragment.query) { creating = u } }

            // Ассистент (0.6): спрашивают кнопкой над словом; здесь — «думает…» и ответ карточкой, без «Добавить» ничего не пишется
            if (app.fitfat.data.Assistant.available) {
                val reply = vm.assistant
                androidx.compose.animation.AnimatedVisibility(reply == null && vm.assistantBusy) { AssistantBar(busy = true) {} }
                androidx.compose.animation.AnimatedVisibility(reply != null, Modifier.bringIntoViewRequester(bringCard)) {
                    // Держим последний ответ, пока карточка сворачивается
                    val shown = remember { mutableStateOf<app.fitfat.data.Assistant.Reply?>(null) }
                    if (reply != null) shown.value = reply
                    shown.value?.let { r ->
                        AssistantCard(
                            r, onDismiss = vm::dismissAssistant, onAdd = { vm.acceptAssistant(it) },
                            onOption = { vm.askAssistant(ctx, answer = it) }, onRetry = { vm.askAssistant(ctx) },
                        )
                    }
                }
            }

            if (hasItems) {
                val total = vm.picked.fold(Macros()) { a, it -> a + it.macros }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("+${fmt0(total.kcal)} ккал", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(macroLine(total.protein, total.fat, total.carbs), fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("останется", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        Text("${fmt0(left - total.kcal)} ккал", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // «Посмотреть все» есть всегда, даже когда недавних ещё нет
            run {
                Column(verticalArrangement = Arrangement.spacedBy(if (hasItems) 4.dp else 6.dp)) {
                    Row(Modifier.fillMaxWidth().height(36.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(if (recent.isEmpty()) "Продукты и блюда" else "Недавние", Modifier.weight(1f))
                        Surface(onClick = onAllProducts, shape = RoundedCornerShape(18.dp), color = Color.Transparent, modifier = Modifier.height(36.dp)) {
                            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Посмотреть все", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
                                Icon(MockIcons.ChevronRight, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                            }
                        }
                    }
                    if (recent.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(if (hasItems) 22.dp else 24.dp)).background(colors.surfaceContainerLow)) {
                        recent.forEach { p ->
                            if (hasItems) RecentCompact(p) { vm.add(p, null, null) }
                            else RecentRow(p) { portion = FrequentItem(p, p.defaultGrams, if (p.perPiece) p.defaultGrams else null) }
                        }
                    }
                }
            }
        }

        }

        val n = vm.picked.size
        Surface(
            onClick = { vm.save(onClose) }, enabled = hasItems, shape = RoundedCornerShape(28.dp),
            color = if (hasItems) colors.primary else colors.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (hasItems) "Добавить $n ${plural(n)}" else "Добавить",
                    fontSize = if (hasItems) 16.sp else 17.sp, fontWeight = if (hasItems) FontWeight.Medium else FontWeight.SemiBold,
                    letterSpacing = if (hasItems) 0.15.sp else 0.sp,
                    color = if (hasItems) colors.onPrimary else colors.outline,
                )
            }
        }
    }

    pending?.let { (r, p) ->
        val pcs = if (p.perPiece) p.defaultGrams.coerceAtLeast(1.0) else null
        val draft = Picked(0, p, if (p.perPiece) (p.pieceGrams ?: 0.0) * pcs!! else p.defaultGrams, pcs)
        EditPickedDialog(
            draft, adding = true,
            onDismiss = { pending = null }, onRemove = { pending = null },
            onSave = { g, n, ov -> vm.confirm(r, p, if (p.perPiece) null else g, n, ov); pending = null },
        )
    }
    editing?.let { item ->
        EditPickedDialog(
            item, onDismiss = { editing = null },
            onRemove = { vm.remove(item); editing = null },
            onSave = { g, pcs, ov -> vm.update(item, g, pcs, ov); editing = null },
        )
    }
    portion?.let { fr ->
        PortionDialog(fr.product, fr.grams, fr.pieces, onDismiss = { portion = null }) { g, pcs -> vm.add(fr.product, g, pcs); portion = null }
    }
    variants?.let { r ->
        VariantsDialog(
            r, onDismiss = { variants = null },
            onPick = { p -> vm.remember(r, p); variants = null; accept(r, p) },
            onCreate = { variants = null; creating = r },
        )
    }
    creating?.let { r ->
        NewProductSheet(r, onDismiss = { creating = null }) { p, g, pcs -> vm.createProduct(p, r, g, pcs) { creating = null } }
    }
    if (whenOpen) WhenSheet(vm, onDismiss = { whenOpen = false })
    if (consent) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        AssistantConsentDialog(onDismiss = { consent = false }) {
            app.fitfat.data.Assistant.giveConsent(ctx); consent = false; vm.askAssistant(ctx)
        }
    }
}

/**
 * Карточка «Что съели»: скругление 24, без фокуса — `surfaceContainer`, в фокусе — `surfaceContainerHigh`,
 * масштаб 1,03 с пружиной и тенью. Поля 14 (пусто) / 12 (есть продукты).
 */
@Composable
private fun Composer(focused: Boolean, hasItems: Boolean, onTap: () -> Unit, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "scale")
    val elev by animateDpAsState(if (focused) 14.dp else 1.dp, tween(400), label = "elev")
    val bg by animateColorAsState(if (focused) colors.surfaceContainerHigh else colors.surfaceContainer, tween(250), label = "bg")
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.3f) }
            .shadow(elev, shape).clip(shape).background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .padding(if (hasItems) 12.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (hasItems) 8.dp else 6.dp),
    ) {
        Text("Что съели", fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = if (hasItems) 2.dp else 0.dp))
        content()
    }
}

/** Подтверждённый продукт: 56+, поля 6 / 12 / 6 / 6, скругление 16, `surfaceContainerHighest`. */
@Composable
private fun PickedRow(item: Picked, onTap: () -> Unit) {
    val m = item.macros
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onTap, shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerHighest, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Row(Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryIcon(item.product.category, 44.dp, radius = 14.dp, iconSize = 20.dp, photo = item.product.photo)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(item.product.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(item.pieces?.let { "${fmt1(it)} шт · ~${fmt0(item.grams)} г" } ?: "${fmt0(item.grams)} г", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("${fmt0(m.kcal)} ккал", fontSize = 15.sp, fontWeight = FontWeight(650))
                Text(macroLine(m.protein, m.fat, m.carbs), fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** «соус терияки» нет в базе · «+ Создать продукт». Поля 10 / 10 / 10 / 16, скругление 18. */
@Composable
private fun UnknownBanner(word: String, onCreate: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainer).padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            buildAnnotatedString {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append("«$word»"); pop()
                append(" нет в базе")
            },
            fontSize = 14.sp, modifier = Modifier.weight(1f),
        )
        Surface(onClick = onCreate, shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer, modifier = Modifier.height(40.dp)) {
            Row(Modifier.padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(MockIcons.Plus, null, Modifier.size(18.dp), tint = colors.onSurface)
                Text("Создать продукт", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, fontSize = 15.sp, fontWeight = FontWeight(650), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

/** Недавний продукт на пустом экране: 60, плашка 40 / 12, стрелка — к порции. */
@Composable
private fun RecentRow(p: Product, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIcon(p.category, 40.dp, radius = 12.dp, iconSize = 20.dp, photo = p.photo)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(recentSub(p), fontSize = 13.sp, color = colors.onSurfaceVariant)
        }
        Icon(MockIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
    }
}

/** Недавний продукт, когда уже что-то выбрано: 56, плашка 36, «+» 40 — добавить обычную порцию. */
@Composable
private fun RecentCompact(p: Product, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIcon(p.category, 36.dp, radius = 12.dp, iconSize = 18.dp, photo = p.photo)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(recentSub(p), fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        Surface(onClick = onAdd, shape = RoundedCornerShape(12.dp), color = colors.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(MockIcons.Plus, "Добавить ${p.name}", Modifier.size(18.dp), tint = colors.onSurface) }
        }
    }
}

/** «250 г · 133 ккал» или «1 шт · 248 ккал» — обычная порция продукта. */
private fun recentSub(p: Product): String {
    val pcs = if (p.perPiece) p.defaultGrams.coerceAtLeast(1.0) else null
    val kcal = if (p.perPiece) p.kcal * pcs!! else p.kcal * p.defaultGrams / 100
    return "${amount(p, p.defaultGrams, pcs)} · ${fmt0(kcal)} ккал"
}

private fun amount(p: Product, grams: Double, pieces: Double?) =
    if (p.perPiece) "${fmt1(pieces ?: 1.0)} шт" else "${fmt0(grams)} г"

private fun plural(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> "продукт"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "продукта"
    else -> "продуктов"
}

/** Расширяет элемент на [by] за поля родителя с обеих сторон. */
private fun Modifier.bleed(by: androidx.compose.ui.unit.Dp) = layout { m, c ->
    val extra = (by * 2).roundToPx()
    val pl = m.measure(c.copy(minWidth = c.minWidth + extra, maxWidth = c.maxWidth + extra))
    layout(c.maxWidth, pl.height) { pl.place(-extra / 2, 0) }
}

/**
 * Ассистент к месту для фрагмента: его нет в базе, найденный продукт покрывает его не целиком
 * («манты с говядиной» → «Говядина варёная»), или вся строка — вопрос. Запомненный выбор — без ассистента.
 */
private fun wantsAssistant(r: Recognized, text: String): Boolean {
    val best = r.candidates.firstOrNull() ?: return true
    return looksLikeRequest(text) || (best.score != Int.MAX_VALUE && app.fitfat.data.Recognizer.uncovered(r.fragment, best.product).isNotEmpty())
}
