package com.example.iptv

data class Category(
    val category_id: String,
    val category_name: String
)

data class ChannelItem(
    val stream_id: Int,
    val name: String,
    val stream_type: String,
    val stream_icon: String?,
    val url: String
)

object MockData {
    val categories = listOf(
        Category("1", "Intrattenimento & Generalisti"),
        Category("2", "Sport & Calcio"),
        Category("3", "Cinema & Film"),
        Category("4", "Documentari & News")
    )

    val sampleChannels = listOf(
        ChannelItem(
            stream_id = 1,
            name = "Big Buck Bunny (Demo 720p)",
            stream_type = "movie",
            stream_icon = null,
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        ),
        ChannelItem(
            stream_id = 2,
            name = "Elephants Dream (Demo)",
            stream_type = "movie",
            stream_icon = null,
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
        ),
        ChannelItem(
            stream_id = 3,
            name = "For Bigger Blazes (Demo HD)",
            stream_type = "movie",
            stream_icon = null,
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
        ),
        ChannelItem(
            stream_id = 4,
            name = "Tears of Steel (Sci-Fi Demo)",
            stream_type = "movie",
            stream_icon = null,
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
        )
    )
}