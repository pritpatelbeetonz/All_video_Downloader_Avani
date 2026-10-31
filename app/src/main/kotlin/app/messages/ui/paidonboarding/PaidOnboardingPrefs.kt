package app.messages.ui.paidonboarding

import android.content.Context
import android.content.SharedPreferences
import app.messages.attribution.InstallSourceManager

/**
 * Flow A state: the answers, the page the user is on (so a closed app resumes there) and whether
 * the flow is finished. Answers stay on the device; nothing here is sent anywhere.
 */
object PaidOnboardingPrefs {

    private const val NAME = "paid_onboarding"
    private const val KEY_DONE = "done"
    private const val KEY_STEP = "step"
    private const val KEY_INTERESTS = "interests"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** A paid install (google / facebook / unity) that hasn't finished Flow A yet. */
    fun shouldShow(context: Context): Boolean = InstallSourceManager.isAdInstall() && !isDone(context)

    fun isDone(context: Context): Boolean = prefs(context).getBoolean(KEY_DONE, false)

    fun setDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_DONE, true).remove(KEY_STEP).apply()
    }

    fun step(context: Context): Int = prefs(context).getInt(KEY_STEP, 0)

    fun setStep(context: Context, step: Int) {
        prefs(context).edit().putInt(KEY_STEP, step).apply()
    }

    /** The option id picked on a single-choice page (gender, age, category, watch category). */
    fun answer(context: Context, pageKey: String): String? = prefs(context).getString("answer_$pageKey", null)

    fun setAnswer(context: Context, pageKey: String, optionId: String) {
        prefs(context).edit().putString("answer_$pageKey", optionId).apply()
    }

    fun interests(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_INTERESTS, emptySet())?.toSet() ?: emptySet()

    fun setInterests(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet(KEY_INTERESTS, ids).apply()
    }
}
