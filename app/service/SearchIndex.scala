package service

import com.tumblr.jumblr.types.{Post, TextPost}
import org.tartarus.martin.Stemmer

object Doc {
    def getNormalizedTermList(text: String): List[String] = {
        text.toLowerCase().split("[^\\w]+").map((term) => {
            val stemmer = new Stemmer()
            term.foreach(stemmer.add)
            stemmer.stem()
            stemmer.toString
        }).toList
    }
}

abstract class Doc(val text: String) {
    private val normalizedTerms = Doc.getNormalizedTermList(text)
    val termFrequency = normalizedTerms.groupBy(identity).map({case (term, list) => (term, list.length)})
    val lengthInWords = normalizedTerms.length
}

class TumblrDoc(val post: Post) extends Doc(post.asInstanceOf[TextPost].getBody.replaceAll("<[^>]*>", " "))

class SearchIndex {
    var docs: Seq[Doc] = List()

    def query(tumblrPosts: Seq[Post], query: String): Seq[Doc] = {
        docs = tumblrPosts.map(post => new TumblrDoc(post))
        val idf = fillIDF()
        val avgDocLen = avgDocLength()
        val queryTerms = Doc.getNormalizedTermList(query)
        val sortedDocs = docs.map(doc => (doc, queryTerms)).map({ case (doc, terms) => (doc, terms.map(queryTerm =>
            bm25TermScore(idf.getOrElse(queryTerm, 0), doc.termFrequency.getOrElse(queryTerm, 0), doc.lengthInWords, avgDocLen, 2.0, 0.75)).sum)
            }).filter({ case (doc, score) => score > 0 }).sortBy({ case (doc, score) => -score })
        sortedDocs.foreach({case (doc, score) => println(doc.asInstanceOf[TumblrDoc].post.asInstanceOf[TextPost].getTitle + ": " + score)})
        sortedDocs.map({case (doc, score) => doc}).take(5)
    }

    def fillIDF(): Map[String, Int] = {
        docs.flatMap(_.termFrequency.keys).groupBy(identity).map({case (term, list) => (term, list.length)})
    }

    def avgDocLength(): Int = {
        docs.map(doc => doc.lengthInWords).sum / docs.length
    }

    def bm25TermScore(idf: Int, termFrequency: Int, lengthInWords: Int, avgDocLength: Double, k1: Double, b: Double): Double = {
        idf * (termFrequency * (k1 + 1)) / (termFrequency + k1 * (1 - b + b * lengthInWords / avgDocLength))
    }
}
