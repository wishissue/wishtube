package app.wishtube.data

import kotlinx.serialization.Serializable
import app.wishtube.recommend.RecWeights

/** Portable JSON export of one profile. Contains only data that lives on this device. */
@Serializable data class ExpPlaylist(val name: String, val videoIds: List<String>)

@Serializable
data class ProfileExport(
    val version: Int = 1,
    val name: String,
    val videos: List<CachedVideo>,
    val entries: List<LibraryEntry>,
    val playlists: List<ExpPlaylist>,
    val notes: List<NoteEntity>,
    val follows: List<FollowEntity>,
    val hiddenCreators: List<HiddenCreator>,
    val hiddenTopics: List<HiddenTopic>,
    val searches: List<SearchEntity>,
    val weights: RecWeights,
)
