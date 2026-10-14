package app.messages.data.model

data class OnboardingModel(
    val imageRes: Int,
    val title: String,
    val message: String,
    val videoRes: Int? = null
)
