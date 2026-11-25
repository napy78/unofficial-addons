package com.napy.musicplayer.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Un brano salvato nel database locale. */
@Entity(tableName = "tracks", indices = [Index("folder"), Index("title")])
data class Track(
    @PrimaryKey val id: Long,          // _ID di MediaStore
    val uri: String,
    val path: String,
    val folder: String,                // cartella che lo contiene
    val fileName: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val mime: String,
    val size: Long,
    val modified: Long                 // per l'aggiornamento incrementale
)

/** Albero delle cartelle precalcolato: la navigazione e' una semplice query indicizzata. */
@Entity(tableName = "folders", indices = [Index("parent")])
data class Folder(
    @PrimaryKey val path: String,
    val parent: String,
    val name: String,
    val trackCount: Int,               // brani totali (sottocartelle incluse)
    val ownCount: Int,                 // brani direttamente in questa cartella
    // brano rappresentativo, usato per ricavare la locandina della cartella
    val artUri: String,
    val artPath: String,
    val artArtist: String,
    val artAlbum: String,
    val artTitle: String
)

/** Cartella esclusa dalla scansione (vale anche per tutte le sue sottocartelle). */
/** Cartella scelta per la scansione (include le sottocartelle). Tabella vuota = si scansiona tutto. */
@Entity(tableName = "included")
data class Included(@PrimaryKey val path: String)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM included ORDER BY path COLLATE NOCASE") fun included(): Flow<List<Included>>
    @Query("SELECT * FROM included") suspend fun includedOnce(): List<Included>
    @Query("DELETE FROM included") suspend fun clearIncluded()
    @Insert suspend fun insertIncluded(l: List<Included>)

    @Query("SELECT * FROM folders ORDER BY path COLLATE NOCASE")
    fun allFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM tracks WHERE folder IN (:folders) ORDER BY fileName COLLATE NOCASE")
    fun tracksInFolders(folders: List<String>): Flow<List<Track>>

    @Query(
        "SELECT * FROM tracks WHERE folder = :folder OR substr(folder, 1, length(:folder) + 1) = :folder || '/' " +
            "ORDER BY folder COLLATE NOCASE, fileName COLLATE NOCASE"
    )
    suspend fun tracksRecursive(folder: String): List<Track>

    @Query(
        "SELECT * FROM tracks WHERE title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' " +
            "OR album LIKE '%' || :q || '%' OR fileName LIKE '%' || :q || '%' ORDER BY title COLLATE NOCASE"
    )
    fun search(q: String): Flow<List<Track>>

    @Query("SELECT COUNT(*) FROM tracks")
    fun trackCount(): Flow<Int>

    @Query("SELECT id, modified FROM tracks")
    suspend fun stamps(): List<Stamp>

    @Upsert suspend fun upsertTracks(t: List<Track>)
    @Query("DELETE FROM tracks WHERE id IN (:ids)") suspend fun deleteTracks(ids: List<Long>)
    @Query("DELETE FROM folders") suspend fun clearFolders()
    @Insert suspend fun insertFolders(f: List<Folder>)
    @Query("SELECT folder, uri, path, artist, album, title FROM tracks ORDER BY fileName COLLATE NOCASE")
    suspend fun repRows(): List<RepRow>
}

data class Stamp(val id: Long, val modified: Long)
data class RepRow(val folder: String, val uri: String, val path: String, val artist: String, val album: String, val title: String)

@Database(entities = [Track::class, Folder::class, Included::class], version = 4, exportSchema = false)
abstract class LibraryDb : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        @Volatile private var inst: LibraryDb? = null
        fun get(ctx: Context): LibraryDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, LibraryDb::class.java, "library.db")
                .addMigrations(object : androidx.room.migration.Migration(2, 3) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `excluded` (`path` TEXT NOT NULL, PRIMARY KEY(`path`))")
                    }
                })
                .addMigrations(object : androidx.room.migration.Migration(3, 4) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("DROP TABLE IF EXISTS `excluded`")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `included` (`path` TEXT NOT NULL, PRIMARY KEY(`path`))")
                    }
                })
                .fallbackToDestructiveMigration().build().also { inst = it }
        }
    }
}
