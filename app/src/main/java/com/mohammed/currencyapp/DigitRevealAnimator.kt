package com.mohammed.currencyapp

import kotlin.random.Random

/**
 * منطق أنيميشن "الأرقام المبعثرة تستقر تدريجياً على الرقم الصحيح" (نفس فكرة
 * ملف العداد الـ HTML الأصلي)، بس مستخرج بمكان واحد مشترك بدل ما يتكرر
 * بكل مكان يحتاجه (ودجت الشاشة الرئيسية، وقائمة الأسعار بالتطبيق).
 *
 * الفواصل والحروف بالنص (مثل "," و"د.ع" أو "يورو") تبقى ثابتة بمكانها،
 * فقط خانات الأرقام [0-9] هي اللي تتحرك بكل إطار.
 */
object DigitRevealAnimator {

    /** خطة الأنيميشن المحسوبة مرة وحدة من النص النهائي: مواقع خانات الأرقام
     * بالنص، قيمة كل خانة بالهدف، والرقم الهدف الكامل (كل الأرقام مجمّعة). */
    data class Plan(
        val digitPositions: List<Int>,
        val targetDigits: List<Int>,
        val targetNumber: Long
    )

    /** يحلل نص السعر النهائي ويجهز خطة الأنيميشن، أو null لو ما فيه أي رقم
     * صالح بالنص (عندها يُعرض النص كما هو مباشرة بدون أنيميشن). */
    fun plan(finalText: String): Plan? {
        val positions = finalText.indices.filter { finalText[it].isDigit() }
        if (positions.isEmpty()) return null
        val digits = positions.map { finalText[it] - '0' }
        val number = digits.joinToString("") { it.toString() }.toLongOrNull() ?: return null
        return Plan(positions, digits, number)
    }

    /**
     * يبني النص المعروض بخطوة معينة من الأنيميشن. step يبدأ من 1، وبآخر خطوة
     * (step == totalSteps) يرجع النص الحقيقي الكامل دايماً.
     */
    fun frameText(finalText: String, plan: Plan, step: Int, totalSteps: Int): String {
        val progress = step.toFloat() / totalSteps
        val currentRealValue = (plan.targetNumber.toDouble() * progress.coerceAtMost(1f)).toLong()
        val digitCount = plan.targetDigits.size
        val realDigits = currentRealValue.toString()
            .padStart(digitCount, '0')
            .takeLast(digitCount)
            .map { it - '0' }

        val display = StringBuilder(finalText)
        for ((idx, pos) in plan.digitPositions.withIndex()) {
            val settled = step >= totalSteps || (realDigits[idx] == plan.targetDigits[idx] && progress > 0.6f)
            val shown = if (settled) plan.targetDigits[idx] else Random.nextInt(10)
            display.setCharAt(pos, '0' + shown)
        }
        return display.toString()
    }
}
