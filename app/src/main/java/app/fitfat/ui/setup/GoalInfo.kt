package app.fitfat.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.Goal
import app.fitfat.data.NormsCalc
import app.fitfat.data.NormsPlan
import app.fitfat.data.Profile
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmtF1

fun goalTitle(g: Goal) = when (g) {
    Goal.Recomp -> "Рекомпозиция"
    Goal.Lose -> "Похудение"
    Goal.Gain -> "Набор мышц"
    Goal.Keep -> "Поддержание"
}

/**
 * Подробности цели (решение автора 08.11.2026): что это, кому подходит, подвохи, как понять, что работает, и как
 * приложение считает нормы — с числами профиля [p] при весе [weight]. Под каруселью цели в настройке и в шторке
 * цели на «Тело и цель». Задача текстов — чтобы человек, который тренируется, не выбирал «Похудение» по привычке:
 * для него рекомпозиция почти всегда выгоднее.
 */
@Composable
fun GoalDetails(p: Profile, weight: Double) {
    AnimatedContent(
        p.goal, label = "goalInfo",
        transitionSpec = { fadeIn(tween(220, delayMillis = 60)).togetherWith(fadeOut(tween(120))) },
    ) { g ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            goalSections(p.copy(goal = g), weight).forEach { (h, t) -> Block(h, t) }
        }
    }
}

@Composable
private fun Block(title: String, text: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainer).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
        Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant)
    }
}

private fun goalSections(p: Profile, weight: Double): List<Pair<String, String>> {
    val b = p.body(weight)
    val bmr = fmt0(NormsCalc.bmr(b))
    val base = fmt0(NormsCalc.kcalBase(b))
    val pk = fmtF1(NormsPlan.proteinPerKg(p, weight))
    val carbs = fmt0(NormsCalc.TRAINING_CARBS)
    return when (p.goal) {
        Goal.Recomp -> listOf(
            "Что это" to "Процесс, когда человек одновременно сжигает жир и набирает мышечную массу. Вес почти не меняется, а тело меняется: талия уже, плечи и руки плотнее, одежда сидит иначе. При той же цифре на весах вы выглядите заметно лучше.",
            "Кому подходит" to "Тем, кто тренируется или готов начать. Особенно хорошо — новичкам в зале и тем, кто вернулся после перерыва: их мышцы растут быстрее всего. Процент жира — средний: примерно 15–25 % у мужчин и 22–32 % у женщин. И всем, кому важнее, как выглядит тело, чем цифра на весах.",
            "Почему часто лучше похудения" to "При обычном похудении вместе с жиром уходят и мышцы — до трети сброшенного веса. Тело становится меньше, но мягче, обмен падает, вес легко возвращается. Рекомпозиция мышцы сохраняет и растит: обмен остаётся выше, есть можно больше, а результат держится.",
            "Нюансы" to "По весам медленно: первые 1–2 месяца цифра может почти стоять — это нормально. Смотрите на талию, фото раз в 2 недели и рабочие веса в зале. Без силовых 2–4 раза в неделю не работает. Нужны белок каждый день и сон от 7 часов.",
            "Как считаем" to "Калории — на уровне обмена в покое ($bmr ккал): небольшой дефицит, жир уходит за счёт движения в течение дня. Белок — $pk г на кг веса, чтобы мышцам было из чего расти. В дни тренировок — +$carbs г углеводов на работу и восстановление.",
        )
        Goal.Lose -> listOf(
            "Что это" to "Снижение веса за счёт дефицита калорий. Здоровый темп — 0,3–0,5 кг в неделю.",
            "Кому подходит" to "Когда жира много — примерно больше 25 % у мужчин и 32 % у женщин — и главная задача снизить вес: для здоровья, давления, суставов, самочувствия. С силовыми тренировками уходит в основном жир.",
            "Подвох" to "Дефицит забирает не только жир, но и мышцы — особенно без силовых. Итог: вес меньше, а тело дряблое, сил меньше, обмен ниже, и после похудения вес возвращается быстрее. Если вы тренируетесь и жира не очень много, рекомпозиция даст лучший вид при меньших ограничениях.",
            "Нюансы" to "Чем меньше жира, тем легче вместе с ним потерять мышцы — тогда лучше рекомпозиция. Весы прыгают на 0,5–1 кг из-за воды и соли — смотрите на тренд на вкладке «Вес». Если вес стоит две недели, приложение предложит срезать 150 ккал.",
            "Как считаем" to "Калории — на уровне обмена в покое ($bmr ккал): дефицит получается за счёт движения в течение дня. Белок высокий — $pk г на кг веса, он защищает мышцы. Прогулки и йога помогают худеть, но калорий к норме не добавляют.",
        )
        Goal.Gain -> listOf(
            "Что это" to "Рост мышечной массы на небольшом профиците калорий. Здоровый темп — +0,2–0,4 кг в неделю.",
            "Кому подходит" to "Худощавым — примерно меньше 15 % жира у мужчин и 22 % у женщин, — кто регулярно тренируется и хочет больше мышц и силы.",
            "Нюансы" to "Вместе с мышцами всегда прибавляется немного жира — это цена набора. Быстрее 0,4 кг в неделю растёт уже в основном жир. Без роста рабочих весов в зале профицит уходит в жир. Если жира больше средних значений — начните с рекомпозиции.",
            "Как понять, что работает" to "Тренд веса медленно растёт, рабочие веса тоже, а талия почти не меняется.",
            "Как считаем" to "Калории — расход с бытовым движением и небольшой профицит: $base ккал. Белок — $pk г на кг веса, остальное — углеводы, топливо для тренировок.",
        )
        Goal.Keep -> listOf(
            "Что это" to "Держим текущий вес и форму: едим примерно по расходу.",
            "Кому подходит" to "После похудения или набора: пауза на 1–3 месяца закрепляет результат, и вес не откатывается. И тем, кто доволен формой и не хочет меняться.",
            "Нюансы" to "Мышцы без нагрузки всё равно постепенно уходят — тренировки нужны и здесь. Если тренируетесь и хотите при этом подтянуть тело, выберите рекомпозицию: калорий чуть меньше, а форма будет улучшаться.",
            "Как понять, что работает" to "Тренд веса держится в пределах ±1 кг. Если уходит дальше — смените цель.",
            "Как считаем" to "Калории — по расходу с бытовым движением: $base ккал. Белок — $pk г на кг веса.",
        )
    }
}
