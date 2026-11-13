package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Card
import com.yonidev.ydeck.link.CardItem
import org.json.JSONObject

/**
 * An answer to the phone, read into a [Card]: Claude writes JSON (see Assistant's card format), and here its projects
 * become ids, and each line is checked against that project's lists, so the phone offers "done" or "take it" only on
 * a line that is really there, word for word.
 */
object Cards {
    /**
     * The card in [answer], or null when there is none (the phone then shows the words as they came). [lists] gives a
     * project's left and ideas, by id.
     */
    fun read(answer: String, projects: List<Project>, lists: (String) -> Pair<List<String>, List<String>>): Card? = runCatching {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val card = Card.of(JSONObject(answer.substring(start, end + 1))) ?: return null
        val byId = projects.associateBy { it.id }
        val byName = projects.associateBy { it.name.lowercase() }
        val known = HashMap<String, Pair<List<String>, List<String>>>()
        card.copy(groups = card.groups.take(4).map { g ->
            g.copy(items = g.items.take(6).map { item ->
                val p = byId[item.project] ?: byName[item.project.lowercase()]
                if (p == null) return@map CardItem("", item.text)
                val (left, ideas) = known.getOrPut(p.id) { lists(p.id) }
                val from = when (item.text) {
                    in left -> CardItem.FROM_LEFT
                    in ideas -> CardItem.FROM_IDEAS
                    else -> ""
                }
                CardItem(p.id, item.text, from)
            })
        })
    }.getOrNull()

    /** A project's left and ideas, as its folder has them now. */
    fun lists(id: String): Pair<List<String>, List<String>> {
        val dir = Settings.state.value.localOf(id).dir ?: return emptyList<String>() to emptyList()
        val state = ProjectFiles.readLocal(dir)
        return state?.left.orEmpty() to state?.ideas.orEmpty()
    }
}
