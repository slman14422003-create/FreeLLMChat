package com.freellm.chat.domain

object Concepts {
    private val groups: List<List<String>> = listOf(
        listOf("سعر", "ثمن", "تكلفة", "كلفة", "price", "cost"),
        listOf("شراء", "اشتري", "buy", "purchase"),
        listOf("اصلاح", "تصليح", "fix", "repair"),
        listOf("خطأ", "عطل", "مشكلة", "خلل", "error", "bug", "issue", "problem", "fault"),
        listOf("حذف", "مسح", "ازالة", "delete", "remove", "erase"),
        listOf("تثبيت", "تنصيب", "install", "setup"),
        listOf("تحميل", "تنزيل", "download"),
        listOf("مجاني", "مجانا", "free"),
        listOf("سريع", "بسرعة", "fast", "quick", "quickly"),
        listOf("كبير", "ضخم", "big", "large", "huge"),
        listOf("صغير", "small", "tiny"),
        listOf("تعلم", "تعليم", "learn", "study"),
        listOf("معنى", "تعريف", "مفهوم", "meaning", "definition", "define"),
        listOf("فرق", "اختلاف", "difference"),
        listOf("افضل", "احسن", "best", "top"),
        listOf("هاتف", "جوال", "موبايل", "phone", "mobile", "smartphone"),
        listOf("حاسوب", "كمبيوتر", "computer", "laptop"),
        listOf("تطبيق", "app", "application"),
        listOf("انترنت", "internet", "network", "شبكة"),
        listOf("مال", "فلوس", "نقود", "money", "cash"),
        listOf("طبيب", "دكتور", "doctor", "physician"),
        listOf("مرض", "داء", "disease", "illness", "sickness"),
        listOf("دواء", "علاج", "medicine", "drug", "treatment"),
        listOf("طبخ", "طهي", "cook", "cooking"),
        listOf("وصفة", "recipe"),
        listOf("ترجمة", "translate", "translation"),
        listOf("بداية", "بدء", "start", "begin"),
        listOf("نهاية", "انتهاء", "end", "finish"),
        listOf("سيارة", "عربية", "car", "vehicle"),
        listOf("منزل", "بيت", "house", "home"),
        listOf("عمل", "وظيفة", "job", "career"),
        listOf("مدرسة", "school"),
        listOf("جامعة", "university", "college"),
        listOf("امن", "حماية", "security", "protection"),
        listOf("كلمة السر", "password"),
        listOf("بطاريتي", "بطارية", "battery"),
        listOf("ذاكرة", "memory", "ram"),
        listOf("سرعة", "speed")
    )

    private val canon: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        for (g in groups) {
            val forms = g.map { LocalBrain.stem(LocalBrain.normalize(it)) }.filter { it.isNotEmpty() && ' ' !in it }
            val head = forms.firstOrNull() ?: continue
            forms.forEach { m.putIfAbsent(it, head) }
        }
        m
    }

    fun canonical(token: String): String = canon[token] ?: token
}
