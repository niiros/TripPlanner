package com.niiros.tripplanner.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** ISO-8601 date. */
    val date: String,
    /** HH:mm in the start point's zone. */
    val departureTime: String,
    val startName: String?,
    val startAddress: String?,
    val startLat: Double?,
    val startLng: Double?,
    val startPlaceId: String?,
    val startZoneId: String?,
    val startElevation: Double?,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "stops",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class StopEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val position: Int,
    val name: String,
    val address: String?,
    val lat: Double,
    val lng: Double,
    val placeId: String?,
    val dwellMinutes: Long,
    val needsDaylight: Boolean,
    val minchaHere: Boolean,
    val arvitHere: Boolean,
    val actualDepartureEpochSec: Long?,
    val zoneId: String?,
    val elevation: Double?,
)

data class TripWithStops(
    @Embedded val trip: TripEntity,
    @Relation(parentColumn = "id", entityColumn = "tripId") val stops: List<StopEntity>,
)

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY date DESC, updatedAtEpochMs DESC")
    fun observeTrips(): Flow<List<TripEntity>>

    @Transaction
    @Query("SELECT * FROM trips WHERE id = :id")
    fun observeTrip(id: String): Flow<TripWithStops?>

    @Transaction
    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTrip(id: String): TripWithStops?

    @Upsert
    suspend fun upsertTrip(trip: TripEntity)

    @Upsert
    suspend fun upsertStops(stops: List<StopEntity>)

    @Query("DELETE FROM stops WHERE tripId = :tripId")
    suspend fun deleteStops(tripId: String)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTrip(id: String)

    @Transaction
    suspend fun saveTrip(trip: TripEntity, stops: List<StopEntity>) {
        upsertTrip(trip)
        deleteStops(trip.id)
        upsertStops(stops)
    }
}

@Database(entities = [TripEntity::class, StopEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
}
