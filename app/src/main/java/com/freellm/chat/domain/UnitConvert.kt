package com.freellm.chat.domain

import java.math.BigDecimal
import java.math.MathContext

object UnitConvert {
    private class U(val dim: String, val factor: Double, val offset: Double)

    private val units = HashMap<String, U>()
    private val exact = HashMap<String, U>()

    private fun add(dim: String, factor: Double, offset: Double, vararg names: String) {
        val u = U(dim, factor, offset)
        names.forEach { units[it.lowercase()] = u }
    }

    private fun addExact(dim: String, factor: Double, vararg names: String) {
        val u = U(dim, factor, 0.0)
        names.forEach { exact[it] = u }
    }

    init {
        add("length", 1.0, 0.0, "m", "meter", "meters", "metre", "metres", "\u0645\u062A\u0631", "\u0627\u0645\u062A\u0627\u0631", "\u0623\u0645\u062A\u0627\u0631")
        add("length", 1000.0, 0.0, "km", "kilometer", "kilometers", "kilometre", "kilometres", "\u0643\u0645", "\u0643\u064A\u0644\u0648\u0645\u062A\u0631", "\u0643\u064A\u0644\u0648\u0645\u062A\u0631\u0627\u062A")
        add("length", 0.01, 0.0, "cm", "centimeter", "centimeters", "\u0633\u0645", "\u0633\u0646\u062A\u064A\u0645\u062A\u0631", "\u0633\u0646\u062A\u0645\u062A\u0631")
        add("length", 0.001, 0.0, "mm", "millimeter", "millimeters", "\u0645\u0644\u0645", "\u0645\u0644\u064A\u0645\u062A\u0631")
        add("length", 1e-6, 0.0, "um", "micrometer", "micron")
        add("length", 1609.344, 0.0, "mi", "mile", "miles", "\u0645\u064A\u0644", "\u0623\u0645\u064A\u0627\u0644")
        add("length", 0.9144, 0.0, "yd", "yard", "yards", "\u064A\u0627\u0631\u062F\u0629")
        add("length", 0.3048, 0.0, "ft", "foot", "feet", "\u0642\u062F\u0645")
        add("length", 0.0254, 0.0, "in", "inch", "inches", "\u0628\u0648\u0635\u0629", "\u0627\u0646\u0634", "\u0625\u0646\u0634")
        add("length", 1852.0, 0.0, "nmi", "nauticalmile", "nauticalmiles")

        add("mass", 1.0, 0.0, "kg", "kilogram", "kilograms", "kilo", "kilos", "\u0643\u062C\u0645", "\u0643\u064A\u0644\u0648", "\u0643\u064A\u0644\u0648\u063A\u0631\u0627\u0645", "\u0643\u064A\u0644\u0648\u062C\u0631\u0627\u0645")
        add("mass", 0.001, 0.0, "g", "gram", "grams", "\u063A\u0631\u0627\u0645", "\u062C\u0631\u0627\u0645", "\u063A")
        add("mass", 1e-6, 0.0, "mg", "milligram", "milligrams", "\u0645\u0644\u063A", "\u0645\u0644\u062C\u0645")
        add("mass", 1000.0, 0.0, "t", "tonne", "tonnes", "ton", "tons", "\u0637\u0646")
        add("mass", 0.45359237, 0.0, "lb", "lbs", "pound", "pounds", "\u0631\u0637\u0644", "\u0628\u0627\u0648\u0646\u062F")
        add("mass", 0.028349523125, 0.0, "oz", "ounce", "ounces", "\u0627\u0648\u0642\u064A\u0629", "\u0623\u0648\u0642\u064A\u0629")
        add("mass", 6.35029318, 0.0, "st", "stone")

        add("volume", 1.0, 0.0, "l", "liter", "liters", "litre", "litres", "\u0644\u062A\u0631")
        add("volume", 0.001, 0.0, "ml", "milliliter", "milliliters", "\u0645\u0644", "\u0645\u0644\u0644")
        add("volume", 1000.0, 0.0, "m3", "cubicmeter")
        add("volume", 0.001, 0.0, "cm3", "cc")
        add("volume", 3.785411784, 0.0, "gal", "gallon", "gallons", "\u062C\u0627\u0644\u0648\u0646")
        add("volume", 0.946352946, 0.0, "qt", "quart", "quarts")
        add("volume", 0.473176473, 0.0, "pt", "pint", "pints")
        add("volume", 0.2365882365, 0.0, "cup", "cups", "\u0643\u0648\u0628")
        add("volume", 0.0295735295625, 0.0, "floz", "fluidounce")
        add("volume", 0.01478676478125, 0.0, "tbsp", "tablespoon")
        add("volume", 0.00492892159375, 0.0, "tsp", "teaspoon")

        add("area", 1.0, 0.0, "m2", "sqm", "squaremeter", "squaremeters", "\u0645\u062A\u0631 \u0645\u0631\u0628\u0639", "\u0645\u062A\u0631\u0645\u0631\u0628\u0639")
        add("area", 1e6, 0.0, "km2", "sqkm", "squarekilometer")
        add("area", 1e-4, 0.0, "cm2", "sqcm")
        add("area", 1e4, 0.0, "ha", "hectare", "hectares", "\u0647\u0643\u062A\u0627\u0631")
        add("area", 4046.8564224, 0.0, "acre", "acres", "\u0641\u062F\u0627\u0646")
        add("area", 0.09290304, 0.0, "ft2", "sqft", "squarefoot", "squarefeet")
        add("area", 6.4516e-4, 0.0, "in2", "sqin")
        add("area", 2589988.110336, 0.0, "mi2", "sqmi", "squaremile")

        add("speed", 1.0, 0.0, "m/s", "mps")
        add("speed", 1.0 / 3.6, 0.0, "km/h", "kmh", "kph", "kmph", "\u0643\u0645/\u0633")
        add("speed", 0.44704, 0.0, "mph", "mi/h")
        add("speed", 0.514444444, 0.0, "kn", "knot", "knots", "\u0639\u0642\u062F\u0629")
        add("speed", 0.3048, 0.0, "ft/s", "fps")

        add("time", 1.0, 0.0, "s", "sec", "second", "seconds", "\u062B\u0627\u0646\u064A\u0629", "\u062B\u0648\u0627\u0646\u064A")
        add("time", 0.001, 0.0, "ms", "millisecond", "milliseconds")
        add("time", 60.0, 0.0, "min", "minute", "minutes", "\u062F\u0642\u064A\u0642\u0629", "\u062F\u0642\u0627\u0626\u0642")
        add("time", 3600.0, 0.0, "h", "hr", "hour", "hours", "\u0633\u0627\u0639\u0629", "\u0633\u0627\u0639\u0627\u062A")
        add("time", 86400.0, 0.0, "d", "day", "days", "\u064A\u0648\u0645", "\u0627\u064A\u0627\u0645", "\u0623\u064A\u0627\u0645")
        add("time", 604800.0, 0.0, "wk", "week", "weeks", "\u0623\u0633\u0628\u0648\u0639", "\u0627\u0633\u0628\u0648\u0639", "\u0627\u0633\u0627\u0628\u064A\u0639", "\u0623\u0633\u0627\u0628\u064A\u0639")
        add("time", 2629746.0, 0.0, "month", "months", "\u0634\u0647\u0631", "\u0627\u0634\u0647\u0631", "\u0623\u0634\u0647\u0631")
        add("time", 31556952.0, 0.0, "yr", "year", "years", "\u0633\u0646\u0629", "\u0633\u0646\u0648\u0627\u062A", "\u0639\u0627\u0645")

        addExact("data", 0.125, "bit", "bits")
        addExact("data", 125.0, "kbit", "Kbit")
        addExact("data", 125000.0, "Mbit", "Mb")
        addExact("data", 125000000.0, "Gbit", "Gb")
        addExact("data", 1.0, "B", "byte", "bytes")
        addExact("data", 1000.0, "KB", "kB")
        addExact("data", 1e6, "MB")
        addExact("data", 1e9, "GB")
        addExact("data", 1e12, "TB")
        addExact("data", 1024.0, "KiB")
        addExact("data", 1048576.0, "MiB")
        addExact("data", 1073741824.0, "GiB")
        addExact("data", 1099511627776.0, "TiB")

        add("data", 1000.0, 0.0, "kb", "kilobyte", "kilobytes")
        add("data", 1e6, 0.0, "mb", "megabyte", "megabytes", "\u0645\u064A\u063A\u0627\u0628\u0627\u064A\u062A", "\u0645\u064A\u062C\u0627\u0628\u0627\u064A\u062A")
        add("data", 1e9, 0.0, "gb", "gigabyte", "gigabytes", "\u062C\u064A\u062C\u0627\u0628\u0627\u064A\u062A", "\u063A\u064A\u063A\u0627\u0628\u0627\u064A\u062A")
        add("data", 1e12, 0.0, "tb", "terabyte", "terabytes")

        add("temp", 1.0, 273.15, "c", "celsius", "centigrade", "\u0633\u064A\u0644\u0632\u064A\u0648\u0633", "\u0645\u0626\u0648\u064A\u0629", "\u062F\u0631\u062C\u0629 \u0645\u0626\u0648\u064A\u0629")
        add("temp", 5.0 / 9.0, 273.15 - 160.0 / 9.0, "f", "fahrenheit", "\u0641\u0647\u0631\u0646\u0647\u0627\u064A\u062A")
        add("temp", 1.0, 0.0, "k", "kelvin", "\u0643\u0644\u0641\u0646")

        add("pressure", 1.0, 0.0, "pa", "pascal")
        add("pressure", 1000.0, 0.0, "kpa", "kilopascal")
        add("pressure", 1e6, 0.0, "mpa", "megapascal")
        add("pressure", 1e5, 0.0, "bar")
        add("pressure", 100.0, 0.0, "mbar", "hpa")
        add("pressure", 101325.0, 0.0, "atm", "atmosphere")
        add("pressure", 6894.757293168, 0.0, "psi")
        add("pressure", 133.322387415, 0.0, "mmhg")
        add("pressure", 133.322368, 0.0, "torr")

        add("angle", 1.0, 0.0, "rad", "radian", "radians", "\u0631\u0627\u062F\u064A\u0627\u0646")
        add("angle", Math.PI / 180.0, 0.0, "deg", "degree", "degrees", "\u062F\u0631\u062C\u0629")
        add("angle", Math.PI / 200.0, 0.0, "grad", "gradian")
        add("angle", 2.0 * Math.PI, 0.0, "turn", "turns", "\u062F\u0648\u0631\u0629")

        add("energy", 1.0, 0.0, "j", "joule", "joules")
        add("energy", 1000.0, 0.0, "kj", "kilojoule")
        add("energy", 4.184, 0.0, "cal", "calorie")
        add("energy", 4184.0, 0.0, "kcal", "kilocalorie", "\u0633\u0639\u0631\u0629")
        add("energy", 3600.0, 0.0, "wh")
        add("energy", 3.6e6, 0.0, "kwh")
    }

    private val currencyNames = mapOf(
        "\u062F\u0648\u0644\u0627\u0631" to "USD", "\u064A\u0648\u0631\u0648" to "EUR", "\u0631\u064A\u0627\u0644" to "SAR", "\u062F\u0631\u0647\u0645" to "AED", "\u062C\u0646\u064A\u0647" to "EGP",
        "\u062F\u064A\u0646\u0627\u0631" to "KWD", "\u0644\u064A\u0631\u0629" to "TRY", "\u062C\u0646\u064A\u0647 \u0627\u0633\u062A\u0631\u0644\u064A\u0646\u064A" to "GBP", "\u064A\u0646" to "JPY"
    )

    private fun clean(name: String): String =
        name.trim().replace("°", "").replace("²", "2").replace("³", "3").replace(".", "").replace(" per ", "/").trim()

    private fun find(name: String): Pair<String, U>? {
        val c = clean(name)
        exact[c]?.let { return c to it }
        val l = c.lowercase()
        units[l]?.let { return l to it }
        units[l.replace(" ", "")]?.let { return l to it }
        return null
    }

    fun known(name: String): Boolean = find(name) != null

    fun currencyCode(name: String): String? {
        val c = clean(name)
        currencyNames[c]?.let { return it }
        return if (c.length == 3 && c.all { it.isLetter() && it.code < 128 }) c.uppercase() else null
    }

    fun convert(value: Double, from: String, to: String): Double? {
        val a = find(from)?.second ?: return null
        val b = find(to)?.second ?: return null
        if (a.dim != b.dim) return null
        return (value * a.factor + a.offset - b.offset) / b.factor
    }

    fun format(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return v.toString()
        val bd = BigDecimal(v).round(MathContext(10))
        return if (bd.signum() == 0) "0" else bd.stripTrailingZeros().toPlainString()
    }

    fun describe(value: Double, from: String, to: String): String? =
        convert(value, from, to)?.let { format(value) + " " + from.trim() + " = " + format(it) + " " + to.trim() }
}
