package service


import javax.inject.Singleton

import org.tartarus.martin.Stemmer
import play.api.Logger
import play.twirl.api.Html

abstract class Doc(val cacheKey: String, val text: String) {
    private val normalizedTerms = SearchIndex.normalizeString(text)

    val termFrequency: Map[String, Int] = SearchIndex.termFrequency(normalizedTerms)
    val termCount: Int = normalizedTerms.length

    def toHtml: Html
}

case class TumblrDoc(post: TumblrPost)
    extends Doc("tumblr:" + post.id, post.body.replaceAll("<[^>]*>", " ")) {
    override def toHtml: Html = views.html.snippet.blogpost(post, 0, trunc = false)
}

case class ProjectDoc(project: Project)
    extends Doc("github:" + project.repoName, project.description) {
    override def toHtml: Html = views.html.snippet.project(project)
}

@Singleton
class SearchIndex {

    def query(docs: Seq[Doc], query: String): Seq[Doc] = {
        val idf = fillIDF(docs).getOrElse(_: String, 0)
        val avgDocLen = avgDocLength(docs)
        val queryTerms = SearchIndex.normalizeString(query)
        val sortedDocs = docs
            .map(doc => (doc, queryTerms))
            .map({
                case (doc, terms) => (doc, terms.map(queryTerm =>
                bm25TermScore(idf(queryTerm), doc.termFrequency.getOrElse(queryTerm, 0), doc.termCount, avgDocLen, 2.0, 0.75)).sum)
            })
            .filter({ case (_, score) => score > 0 })
            .sortBy({ case (_, score) => -score })
        for ((doc, score) <- sortedDocs) {
            Logger.debug(s"Search score: ${doc.cacheKey}=$score")
        }
        sortedDocs.map({case (doc, _) => doc}).take(5)
    }

    def fillIDF(docs: Seq[Doc]): Map[String, Int] = {
        docs.flatMap(_.termFrequency.keys).groupBy(identity).map({case (term, list) => (term, list.length)})
    }

    def avgDocLength(docs: Seq[Doc]): Int = {
        docs.map(doc => doc.termCount).sum / docs.length
    }

    def bm25TermScore(idf: Int, termFrequency: Int, lengthInWords: Int, avgDocLength: Double, k1: Double, b: Double): Double = {
        idf * (termFrequency * (k1 + 1)) / (termFrequency + k1 * (1 - b + b * lengthInWords / avgDocLength))
    }
}

object SearchIndex {
    def normalizeString(text: String): Seq[String] = {
        text.toLowerCase().split("[^\\w]+").map{ term =>
            val stemmer = new Stemmer()
            term.foreach(stemmer.add)
            stemmer.stem()
            stemmer.toString
        }
    }

    def termFrequency(terms: Seq[String]): Map[String, Int] = {
        terms.groupBy(identity).map({case (term, list) => (term, list.length)})
    }
}
