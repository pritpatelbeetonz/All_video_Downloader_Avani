package app.messages.search

sealed class GoogleSearchFeedItem {
    data class Blog(val post: BlogPost) : GoogleSearchFeedItem()
    data class AdPlaceholder(val id: Int) : GoogleSearchFeedItem()
}
