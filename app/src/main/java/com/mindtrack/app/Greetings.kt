package com.mindtrack.app

import kotlin.random.Random

/**
 * The line at the top of Home: a greeting with the person's first name and a short, casual line that fits
 * the time of day. It varies between visits, and switches to a gentler tone after a Low or Rough check-in.
 */
internal data class Greeting(val title: String, val line: String)

private enum class DayPart { LATE_NIGHT, MORNING, AFTERNOON, EVENING, NIGHT }

private fun dayPartOf(hour: Int): DayPart = when (hour) {
    in 0..4 -> DayPart.LATE_NIGHT
    in 5..11 -> DayPart.MORNING
    in 12..16 -> DayPart.AFTERNOON
    in 17..20 -> DayPart.EVENING
    else -> DayPart.NIGHT
}

private val titles: Map<DayPart, List<String>> = mapOf(
    DayPart.LATE_NIGHT to listOf("Hey, %1\$s!", "Still up, %1\$s?"),
    DayPart.MORNING to listOf("Good morning, %1\$s!", "Hey, %1\$s!", "Morning, %1\$s!"),
    DayPart.AFTERNOON to listOf("Hey, %1\$s!", "What's up, %1\$s?", "Good afternoon, %1\$s!"),
    DayPart.EVENING to listOf("Good evening, %1\$s!", "Hey, %1\$s!", "Welcome back, %1\$s!"),
    DayPart.NIGHT to listOf("Hey, %1\$s!", "Good evening, %1\$s!")
)

private val titlesWithoutName: Map<DayPart, List<String>> = mapOf(
    DayPart.LATE_NIGHT to listOf("Hey there!", "Still up?"),
    DayPart.MORNING to listOf("Good morning!", "Hey there!", "Morning!"),
    DayPart.AFTERNOON to listOf("Hey there!", "What's up?", "Good afternoon!"),
    DayPart.EVENING to listOf("Good evening!", "Hey there!", "Welcome back!"),
    DayPart.NIGHT to listOf("Hey there!", "Good evening!")
)

private val lines: Map<DayPart, List<String>> = mapOf(
    DayPart.LATE_NIGHT to listOf(
        "Night owl, huh?",
        "Still awake? Be gentle with yourself tonight.",
        "Late-night thoughts can feel louder. Sleep helps, when you can."
    ),
    DayPart.MORNING to listOf(
        "Fresh day, fresh start.",
        "Rise and shine. Or just rise, that counts too.",
        "No pressure. Let's see how today feels."
    ),
    DayPart.AFTERNOON to listOf(
        "Halfway through the day. How's it going?",
        "A sip of water and a stretch? Just saying.",
        "Quick pause in the middle of it all?"
    ),
    DayPart.EVENING to listOf(
        "You made it through another day. Proud of you.",
        "Evening vibes. How did today treat you?",
        "Time to slow down a little."
    ),
    DayPart.NIGHT to listOf(
        "Winding down? Perfect time for a check-in.",
        "How was today, really?",
        "A quiet moment before bed."
    )
)

/** Used after a Low or Rough check-in: no jokes, just warmth. */
private val gentleLines = listOf(
    "However you feel is okay.",
    "I'm glad you're here.",
    "Take today one small step at a time.",
    "You don't have to explain anything."
)

/** Short, kind lines for the card on Home. */
internal val gentleReminders = listOf(
    "You don't have to figure everything out today.",
    "Small steps still count.",
    "Rest is productive too.",
    "Be as kind to yourself as you'd be to a friend.",
    "It's okay to take things slowly.",
    "You've gotten through hard days before.",
    "Drink some water. Unclench your jaw. Drop your shoulders.",
    "Your feelings are valid, even the messy ones.",
    "Progress isn't always loud.",
    "Not every day has to be a good one to be worth it.",
    "Today, \"enough\" is enough.",
    "A deep breath is a good place to start.",
    "You're allowed to ask for help.",
    "Comparison is a thief. Run your own race."
)

internal fun greetingFor(firstName: String?, hour: Int, softTone: Boolean, random: Random): Greeting {
    val part = dayPartOf(hour)
    val name = firstName?.trim()?.takeIf { it.isNotEmpty() }
    val title = if (name != null) trf(titles.getValue(part).random(random), name) else tr(titlesWithoutName.getValue(part).random(random))
    val line = tr((if (softTone) gentleLines else lines.getValue(part)).random(random))
    return Greeting(title, line)
}

internal fun reminderFor(random: Random): String = tr(gentleReminders.random(random))

/** The first word of a display name, or null when the person has not set a real name. */
internal fun firstNameOf(displayName: String?): String? {
    val first = displayName?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: return null
    return if (first.equals("MindTrack", ignoreCase = true) || first.equals(tr("MindTrack member").substringBefore(' '), ignoreCase = true)) null else first
}

/** Picked once per app launch so the lines change between visits but don't flicker while using the app. */
internal object HomeSeed {
    val random: Random = Random(System.currentTimeMillis())
    val greetingSeed = random.nextInt()
    val reminderSeed = random.nextInt()
}
