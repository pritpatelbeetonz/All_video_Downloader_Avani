package app.messages.data.model

data class LanguageModel(
    val name: String,
    val nativeName: String,
    val code: String,
    var isSelected: Boolean = false,
    var colorCode: String = ""
)
