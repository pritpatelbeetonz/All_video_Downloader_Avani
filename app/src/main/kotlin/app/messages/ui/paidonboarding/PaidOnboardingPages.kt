package app.messages.ui.paidonboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.messages.R

/** One option on a single-choice page: a coloured circle with [badge], then the label. */
data class ChoiceOption(
    val id: String,
    @StringRes val labelRes: Int,
    val badge: String,
    val color: Int,
)

/** One option on the interests page (a chip). */
data class InterestOption(val id: String, @StringRes val labelRes: Int)

/** A Flow A page. [key] names it in prefs and analytics; [buttonRes] is the bottom button's text. */
sealed class PaidPage(val key: String, @StringRes val buttonRes: Int) {

    class Gender : PaidPage("gender", R.string.paid_onb_next)

    class Choice(
        key: String,
        @StringRes val titleRes: Int,
        @StringRes val subtitleRes: Int,
        val options: List<ChoiceOption>,
    ) : PaidPage(key, R.string.paid_onb_next)

    class Interests(
        @StringRes val titleRes: Int,
        @StringRes val subtitleRes: Int,
        val options: List<InterestOption>,
    ) : PaidPage("interests", R.string.paid_onb_next)

    class Feature(
        key: String,
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int,
        @StringRes val captionRes: Int,
        @DrawableRes val imageRes: Int,
        @StringRes buttonRes: Int = R.string.paid_onb_next,
    ) : PaidPage(key, buttonRes)
}

/**
 * The 11 Flow A pages, in order. Reorder, add or remove pages here; the activity, progress and
 * analytics follow this list.
 */
object PaidOnboardingPages {

    val ALL: List<PaidPage> = listOf(
        // 1
        PaidPage.Gender(),
        // 2
        PaidPage.Choice(
            key = "age",
            titleRes = R.string.paid_onb_age_title,
            subtitleRes = R.string.paid_onb_age_subtitle,
            options = listOf(
                ChoiceOption("18_24", R.string.paid_onb_age_18_24, "18", 0xFF5B8DEF.toInt()),
                ChoiceOption("25_31", R.string.paid_onb_age_25_31, "25", 0xFF34A853.toInt()),
                ChoiceOption("32_39", R.string.paid_onb_age_32_39, "32", 0xFFF5A623.toInt()),
                ChoiceOption("40_plus", R.string.paid_onb_age_40_plus, "40", 0xFFE53935.toInt()),
                ChoiceOption("under_18", R.string.paid_onb_age_under_18, "<18", 0xFF8E8E93.toInt()),
            ),
        ),
        // 3
        PaidPage.Choice(
            key = "category",
            titleRes = R.string.paid_onb_category_title,
            subtitleRes = R.string.paid_onb_category_subtitle,
            options = listOf(
                ChoiceOption("facebook", R.string.paid_onb_category_facebook, "f", 0xFF1877F2.toInt()),
                ChoiceOption("twitter", R.string.paid_onb_category_twitter, "X", 0xFF000000.toInt()),
                ChoiceOption("instagram", R.string.paid_onb_category_instagram, "Ig", 0xFFE1306C.toInt()),
                ChoiceOption("sharechat", R.string.paid_onb_category_sharechat, "S", 0xFFFF6D00.toInt()),
                ChoiceOption("moj", R.string.paid_onb_category_moj, "M", 0xFF1EB6E8.toInt()),
            ),
        ),
        // 4
        PaidPage.Choice(
            key = "watch_category",
            titleRes = R.string.paid_onb_watch_title,
            subtitleRes = R.string.paid_onb_watch_subtitle,
            options = listOf(
                ChoiceOption("hotstar", R.string.paid_onb_watch_hotstar, "H", 0xFF1132D6.toInt()),
                ChoiceOption("prime", R.string.paid_onb_watch_prime, "P", 0xFF00A8E1.toInt()),
                ChoiceOption("sonyliv", R.string.paid_onb_watch_sonyliv, "S", 0xFF8B0000.toInt()),
                ChoiceOption("jiocinema", R.string.paid_onb_watch_jiocinema, "J", 0xFF7B2FF7.toInt()),
            ),
        ),
        // 5
        PaidPage.Interests(
            titleRes = R.string.paid_onb_interests_title,
            subtitleRes = R.string.paid_onb_interests_subtitle,
            options = listOf(
                InterestOption("action_movies", R.string.paid_onb_interest_action_movies),
                InterestOption("comedy_movies", R.string.paid_onb_interest_comedy_movies),
                InterestOption("romance", R.string.paid_onb_interest_romance),
                InterestOption("romantic_songs", R.string.paid_onb_interest_romantic_songs),
                InterestOption("football", R.string.paid_onb_interest_football),
                InterestOption("basketball", R.string.paid_onb_interest_basketball),
                InterestOption("cricket", R.string.paid_onb_interest_cricket),
                InterestOption("travel", R.string.paid_onb_interest_travel),
                InterestOption("food", R.string.paid_onb_interest_food),
            ),
        ),
        // 6
        PaidPage.Feature(
            key = "multiple_format",
            titleRes = R.string.paid_onb_feature_formats_title,
            descriptionRes = R.string.paid_onb_feature_formats_desc,
            captionRes = R.string.paid_onb_feature_formats_caption,
            imageRes = R.drawable.img_paid_onb_feature,
        ),
        // 7
        PaidPage.Feature(
            key = "easy_copy_paste",
            titleRes = R.string.paid_onb_feature_copy_title,
            descriptionRes = R.string.paid_onb_feature_copy_desc,
            captionRes = R.string.paid_onb_feature_copy_caption,
            imageRes = R.drawable.img_paid_onb_feature,
        ),
        // 8
        PaidPage.Feature(
            key = "video_player",
            titleRes = R.string.paid_onb_feature_player_title,
            descriptionRes = R.string.paid_onb_feature_player_desc,
            captionRes = R.string.paid_onb_feature_player_caption,
            imageRes = R.drawable.img_paid_onb_feature,
        ),
        // 9
        PaidPage.Feature(
            key = "no_watermark",
            titleRes = R.string.paid_onb_feature_watermark_title,
            descriptionRes = R.string.paid_onb_feature_watermark_desc,
            captionRes = R.string.paid_onb_feature_watermark_caption,
            imageRes = R.drawable.img_paid_onb_feature,
        ),
        // 10
        PaidPage.Feature(
            key = "hd_video",
            titleRes = R.string.paid_onb_feature_hd_title,
            descriptionRes = R.string.paid_onb_feature_hd_desc,
            captionRes = R.string.paid_onb_feature_hd_caption,
            imageRes = R.drawable.img_paid_onb_feature,
        ),
        // 11
        PaidPage.Feature(
            key = "fast_download",
            titleRes = R.string.paid_onb_feature_speed_title,
            descriptionRes = R.string.paid_onb_feature_speed_desc,
            captionRes = R.string.paid_onb_feature_speed_caption,
            imageRes = R.drawable.img_paid_onb_feature,
            buttonRes = R.string.paid_onb_start_app,
        ),
    )
}
