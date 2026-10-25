package app.iptvtv.data

/** Programy z najbliższych godzin, pogrupowane po id kanału z XMLTV. */
data class EpgSnapshot(
    val now: Long = 0L,
    val byChannel: Map<String, List<ProgrammeEntity>> = emptyMap(),
) {
    /** Zwraca (trwający program, następny program). Dopasowanie po tvg-id z playlisty. */
    fun nowNext(ch: ChannelEntity): Pair<ProgrammeEntity?, ProgrammeEntity?> {
        val key = ch.tvgId?.trim()?.lowercase() ?: return null to null
        val list = byChannel[key] ?: return null to null
        val cur = list.firstOrNull { it.start <= now && it.stop > now }
        val next = list.firstOrNull { it.start >= (cur?.stop ?: now) }
        return cur to next
    }
}
