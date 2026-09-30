package com.mantra.arkod

import org.json.JSONArray
import org.json.JSONObject

/**
 * MOJE ČESTICE AS A FILE, AND THE GROUPS A FILE MAKES (30.9.2026, v8).
 *
 * Marko: *"develop export-import for My Parcels so parcels can be shared between applications on
 * different phones. So one can just send the file and load it in the app, and then it becomes part of
 * the list, with the file name. And also for each parcel file, user has a choice to create custom
 * styling, color for this file and parcels on the map which are going to be marked, lines and
 * other styling."*
 *
 * So Moje čestice are in groups. The parcels he keeps himself are the group with no name; a file
 * opened in the app becomes a group named after the file, and every group has one colour, one line
 * and one weight for all its parcels, and can be hidden. A group goes out as one file, which is
 * JSON with its outlines, so the other phone draws it with no signal and asks the state nothing.
 *
 * Pure: no Android, so Test 1 runs it.
 */
object MarkFile {

    /** What a shared file is called at its end; ".json" too, so any phone knows what opens it. */
    const val SUFFIX = ".arkod.json"

    const val KIND = "arkod-moje-cestice"

    data class Group(
        val name: String,
        val colour: Long = Parcels.SWATCHES.first(),
        val style: Parcels.LineStyle = Parcels.LineStyle.DASHED,
        val weight: ParcelStyle.Weight = ParcelStyle.Weight.NORMAL,
        val visible: Boolean = true,
    )

    /** A file read: the group it becomes and its parcels, already in the group's look. */
    data class Read(val group: Group, val marks: List<Parcels.Mark>)

    /** "Obitelj Boško.arkod.json" is the group "Obitelj Boško". */
    fun nameFrom(fileName: String?): String? =
        fileName?.trim()?.removeSuffix(SUFFIX)?.removeSuffix(".json")?.removeSuffix(".arkod")?.trim()?.takeIf { it.isNotBlank() }

    /** A name a file can carry on any phone: no slashes, colons or other separators. */
    fun fileName(group: String): String =
        (group.ifBlank { "Moje čestice" }.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").trim().ifBlank { "Moje čestice" }) + SUFFIX

    fun encode(group: Group, marks: List<Parcels.Mark>): String = JSONObject().apply {
        put("kind", KIND)
        put("version", 1)
        put("app", "ARKOD Layer")
        put("name", group.name)
        put("colour", java.lang.Long.toHexString(group.colour))
        put("style", group.style.name)
        put("weight", group.weight.name)
        put("parcels", JSONArray().apply {
            marks.forEach { m ->
                put(JSONObject().apply {
                    put("ref", m.reference)
                    put("number", m.number)
                    put("id", m.id)
                    if (m.name.isNotBlank()) put("name", m.name)
                    put("rings", JSONArray().apply {
                        m.rings.forEach { ring ->
                            put(JSONArray().apply { ring.forEach { (la, lo) -> put(round6(la)); put(round6(lo)) } })
                        }
                    })
                })
            }
        })
    }.toString(1)

    private fun round6(v: Double): Double = Math.round(v * 1e6) / 1e6

    /**
     * A file, read. The group takes the file's name when there is one (he may have renamed it before
     * sending), else the name inside. Null when it is not one of ours or holds no parcel.
     */
    fun decode(text: String, fileName: String?): Read? = runCatching {
        val o = JSONObject(text)
        if (o.optString("kind") != KIND) return null
        val name = nameFrom(fileName) ?: o.optString("name").ifBlank { "Uvezeno" }
        val group = Group(
            name = name,
            colour = o.optString("colour").toLongOrNull(16) ?: Parcels.SWATCHES.first(),
            style = Parcels.LineStyle.entries.firstOrNull { it.name == o.optString("style") } ?: Parcels.LineStyle.DASHED,
            weight = ParcelStyle.Weight.entries.firstOrNull { it.name == o.optString("weight") } ?: ParcelStyle.Weight.NORMAL,
        )
        val a = o.optJSONArray("parcels") ?: JSONArray()
        val marks = (0 until a.length()).mapNotNull { i ->
            val p = a.optJSONObject(i) ?: return@mapNotNull null
            val ref = p.optString("ref").takeIf { it.contains('-') } ?: return@mapNotNull null
            val g = p.optJSONArray("rings") ?: JSONArray()
            val rings = (0 until g.length()).mapNotNull { k ->
                val r = g.optJSONArray(k) ?: return@mapNotNull null
                (0 until r.length() / 2).map { j -> r.getDouble(2 * j) to r.getDouble(2 * j + 1) }.takeIf { it.size >= 3 }
            }
            Parcels.Mark(
                reference = ref,
                number = p.optString("number").ifBlank { ref.substringAfter('-') },
                colour = group.colour,
                rings = rings,
                id = p.optLong("id"),
                style = group.style,
                name = p.optString("name"),
                group = group.name,
                weight = group.weight,
            )
        }.distinctBy { it.reference }
        if (marks.isEmpty()) null else Read(group, marks)
    }.getOrNull()

    /**
     * A file opened: its group replaces a group of the same name, its parcels join the list. A
     * parcel is in one group at most, so one already kept elsewhere moves to the file's group.
     */
    fun importInto(marks: List<Parcels.Mark>, groups: List<Group>, read: Read): Pair<List<Parcels.Mark>, List<Group>> {
        val refs = read.marks.map { it.reference }.toSet()
        val kept = marks.filterNot { it.group == read.group.name || it.reference in refs }
        val nextGroups = groups.filterNot { it.name == read.group.name } + read.group
        return (kept + read.marks) to nextGroups
    }

    /** A group restyled: every one of its parcels takes the group's colour, line and weight. */
    fun restyle(marks: List<Parcels.Mark>, group: Group): List<Parcels.Mark> =
        marks.map { if (it.group == group.name) it.copy(colour = group.colour, style = group.style, weight = group.weight) else it }

    /** The group a name is, or a new one in the look its parcels already have. */
    fun groupOf(name: String, groups: List<Group>, marks: List<Parcels.Mark>): Group =
        groups.firstOrNull { it.name == name } ?: marks.firstOrNull { it.group == name }
            ?.let { Group(name, it.colour, it.style, it.weight) } ?: Group(name)

    /** Every group that has parcels, his own (no name) first, then the files in the order they came. */
    fun groupsIn(marks: List<Parcels.Mark>, groups: List<Group>): List<Group> =
        marks.map { it.group }.distinct().sortedBy { if (it.isBlank()) 0 else 1 }.map { groupOf(it, groups, marks) }

    fun encodeGroups(groups: List<Group>): String = JSONArray().apply {
        groups.forEach { g ->
            put(JSONObject().apply {
                put("name", g.name); put("colour", java.lang.Long.toHexString(g.colour))
                put("style", g.style.name); put("weight", g.weight.name); put("visible", g.visible)
            })
        }
    }.toString()

    fun decodeGroups(text: String?): List<Group> = runCatching {
        val a = JSONArray(text ?: "[]")
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Group(
                name = o.optString("name"),
                colour = o.optString("colour").toLongOrNull(16) ?: Parcels.SWATCHES.first(),
                style = Parcels.LineStyle.entries.firstOrNull { it.name == o.optString("style") } ?: Parcels.LineStyle.DASHED,
                weight = ParcelStyle.Weight.entries.firstOrNull { it.name == o.optString("weight") } ?: ParcelStyle.Weight.NORMAL,
                visible = o.optBoolean("visible", true),
            )
        }
    }.getOrDefault(emptyList())
}
