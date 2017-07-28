package service

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

trait BlogPost {
    def anchor: String
    def createdAt: ZonedDateTime

    def formattedDate: String = createdAt.format(BlogPost.format)
    def truncatedBody(paragraphs: Int): String
}

object BlogPost {
    val format: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy")
}
