package app.messages.search

data class BlogPost(
    val id: String,
    val title: String,
    val source: String,
    val timeLabel: String,
    val url: String,
    /** Placeholder tint until remote images / API arrive. */
    val imageColor: Int,
    val imgUrl: String? = null
)
