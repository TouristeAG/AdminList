package com.eventmanager.app.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.eventmanager.app.data.dao.GuestDao
import com.eventmanager.app.data.dao.JobDao
import com.eventmanager.app.data.dao.JobTypeConfigDao
import com.eventmanager.app.data.dao.VenueDao
import com.eventmanager.app.data.dao.VolunteerDao
import com.eventmanager.app.data.dao.CounterDao
import com.eventmanager.app.data.models.Converters
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.VenueEntity
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.data.models.CounterData

@Database(
    entities = [Guest::class, Volunteer::class, Job::class, JobTypeConfig::class, VenueEntity::class, CounterData::class],
    version = 18,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class EventManagerDatabase : RoomDatabase() {
    abstract fun guestDao(): GuestDao
    abstract fun volunteerDao(): VolunteerDao
    abstract fun jobDao(): JobDao
    abstract fun jobTypeConfigDao(): JobTypeConfigDao
    abstract fun venueDao(): VenueDao
    abstract fun counterDao(): CounterDao

    companion object {
        @Volatile
        private var INSTANCE: EventManagerDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add sheetsId column to all tables
                db.execSQL("ALTER TABLE guests ADD COLUMN sheetsId TEXT")
                db.execSQL("ALTER TABLE volunteers ADD COLUMN sheetsId TEXT")
                db.execSQL("ALTER TABLE jobs ADD COLUMN sheetsId TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create job_type_configs table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS job_type_configs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        isShiftJob INTEGER NOT NULL DEFAULT 1,
                        isOrionJob INTEGER NOT NULL DEFAULT 0,
                        requiresShiftTime INTEGER NOT NULL DEFAULT 1,
                        description TEXT NOT NULL DEFAULT '',
                        lastModified INTEGER NOT NULL
                    )
                """)
                
                // Insert default job type configurations
                val defaultJobTypes = listOf(
                    "BAR" to (true to false),
                    "SECURITY" to (true to false),
                    "CLEANING" to (true to false),
                    "SETUP" to (true to false),
                    "SOUND_TECH" to (true to false),
                    "LIGHTING" to (true to false),
                    "ENTRANCE" to (true to false),
                    "CLOAKROOM" to (true to false),
                    "COORDINATION" to (false to true),
                    "COMMITTEE" to (false to true),
                    "COMMISSION_PRESIDENCY" to (false to true),
                    "MEETING" to (true to false),
                    "OTHER" to (true to false)
                )
                
                val currentTime = System.currentTimeMillis()
                defaultJobTypes.forEach { (name, config) ->
                    val (isShiftJob, isOrionJob) = config
                    db.execSQL("""
                        INSERT INTO job_type_configs (name, isActive, isShiftJob, isOrionJob, requiresShiftTime, description, lastModified)
                        VALUES ('$name', 1, ${if (isShiftJob) 1 else 0}, ${if (isOrionJob) 1 else 0}, 1, '', $currentTime)
                    """)
                }
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Add jobTypeName column to jobs table with default value
                    db.execSQL("ALTER TABLE jobs ADD COLUMN jobTypeName TEXT NOT NULL DEFAULT 'OTHER'")
                } catch (e: Exception) {
                    // Column might already exist, ignore the error
                    println("jobTypeName column might already exist: ${e.message}")
                }
                
                try {
                    // Update existing jobs to set jobTypeName based on jobType enum
                    db.execSQL("UPDATE jobs SET jobTypeName = CASE jobType " +
                        "WHEN 'BAR' THEN 'BAR' " +
                        "WHEN 'SECURITY' THEN 'SECURITY' " +
                        "WHEN 'CLEANING' THEN 'CLEANING' " +
                        "WHEN 'SETUP' THEN 'SETUP' " +
                        "WHEN 'SOUND_TECH' THEN 'SOUND_TECH' " +
                        "WHEN 'LIGHTING' THEN 'LIGHTING' " +
                        "WHEN 'ENTRANCE' THEN 'ENTRANCE' " +
                        "WHEN 'CLOAKROOM' THEN 'CLOAKROOM' " +
                        "WHEN 'COORDINATION' THEN 'COORDINATION' " +
                        "WHEN 'COMMITTEE' THEN 'COMMITTEE' " +
                        "WHEN 'COMMISSION_PRESIDENCY' THEN 'COMMISSION_PRESIDENCY' " +
                        "WHEN 'MEETING' THEN 'MEETING' " +
                        "ELSE 'OTHER' END")
                } catch (e: Exception) {
                    println("Failed to update jobTypeName values: ${e.message}")
                }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Add volunteerId column to guests table
                    db.execSQL("ALTER TABLE guests ADD COLUMN volunteerId INTEGER")
                } catch (e: Exception) {
                    // Column might already exist, ignore the error
                    println("volunteerId column might already exist: ${e.message}")
                }
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Fix jobTypeName column to be NOT NULL
                    // First, update any NULL values to 'OTHER'
                    db.execSQL("UPDATE jobs SET jobTypeName = 'OTHER' WHERE jobTypeName IS NULL")
                    
                    // SQLite doesn't support ALTER COLUMN, so we need to recreate the table
                    // Create new jobs table with correct schema
                    db.execSQL("""
                        CREATE TABLE jobs_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            sheetsId TEXT,
                            volunteerId INTEGER NOT NULL,
                            jobType TEXT NOT NULL,
                            jobTypeName TEXT NOT NULL,
                            venue TEXT NOT NULL,
                            date INTEGER NOT NULL,
                            shiftTime TEXT NOT NULL,
                            notes TEXT NOT NULL,
                            lastModified INTEGER NOT NULL
                        )
                    """)
                    
                    // Copy data from old table to new table
                    db.execSQL("""
                        INSERT INTO jobs_new (id, sheetsId, volunteerId, jobType, jobTypeName, venue, date, shiftTime, notes, lastModified)
                        SELECT id, sheetsId, volunteerId, jobType, 
                               COALESCE(jobTypeName, 'OTHER') as jobTypeName, 
                               venue, date, shiftTime, notes, lastModified
                        FROM jobs
                    """)
                    
                    // Drop old table
                    db.execSQL("DROP TABLE jobs")
                    
                    // Rename new table
                    db.execSQL("ALTER TABLE jobs_new RENAME TO jobs")
                    
                } catch (e: Exception) {
                    println("Failed to fix jobTypeName column: ${e.message}")
                    throw e
                }
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Add sheetsId column to job_type_configs table
                    db.execSQL("ALTER TABLE job_type_configs ADD COLUMN sheetsId TEXT")
                    println("Successfully added sheetsId column to job_type_configs table")
                } catch (e: Exception) {
                    // Column might already exist, ignore the error
                    println("sheetsId column might already exist in job_type_configs: ${e.message}")
                }
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Add lastShiftDate column to volunteers table
                    db.execSQL("ALTER TABLE volunteers ADD COLUMN lastShiftDate INTEGER")
                    println("Successfully added lastShiftDate column to volunteers table")
                } catch (e: Exception) {
                    // Column might already exist, ignore the error
                    println("lastShiftDate column might already exist in volunteers: ${e.message}")
                }
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Create new volunteers table with nullable currentRank
                    db.execSQL("""
                        CREATE TABLE volunteers_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            sheetsId TEXT,
                            name TEXT NOT NULL,
                            lastNameAbbreviation TEXT NOT NULL,
                            email TEXT NOT NULL,
                            phoneNumber TEXT NOT NULL,
                            dateOfBirth TEXT NOT NULL,
                            currentRank TEXT,
                            isActive INTEGER NOT NULL,
                            lastShiftDate INTEGER,
                            lastModified INTEGER NOT NULL
                        )
                    """)
                    
                    // Copy data from old table to new table
                    db.execSQL("""
                        INSERT INTO volunteers_new (id, sheetsId, name, lastNameAbbreviation, email, phoneNumber, dateOfBirth, currentRank, isActive, lastShiftDate, lastModified)
                        SELECT id, sheetsId, name, lastNameAbbreviation, email, phoneNumber, dateOfBirth, currentRank, isActive, lastShiftDate, lastModified
                        FROM volunteers
                    """)
                    
                    // Drop old table and rename new table
                    db.execSQL("DROP TABLE volunteers")
                    db.execSQL("ALTER TABLE volunteers_new RENAME TO volunteers")
                    
                    println("Successfully migrated volunteers table to support nullable currentRank")
                } catch (e: Exception) {
                    println("Migration 8_9 failed: ${e.message}")
                    throw e
                }
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Add lastNameAbbreviation column to guests table
                    db.execSQL("ALTER TABLE guests ADD COLUMN lastNameAbbreviation TEXT NOT NULL DEFAULT ''")
                    println("Successfully added lastNameAbbreviation column to guests table")
                } catch (e: Exception) {
                    println("Migration 9_10 failed: ${e.message}")
                    throw e
                }
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Add benefitSystemType column to job_type_configs table
                    db.execSQL("ALTER TABLE job_type_configs ADD COLUMN benefitSystemType TEXT NOT NULL DEFAULT 'STELLAR'")
                    
                    // Add manualRewards column to job_type_configs table
                    db.execSQL("ALTER TABLE job_type_configs ADD COLUMN manualRewards TEXT")
                    
                    println("Successfully added benefitSystemType and manualRewards columns to job_type_configs table")
                } catch (e: Exception) {
                    println("Migration 10_11 failed: ${e.message}")
                    throw e
                }
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Add gender column to volunteers table
                    db.execSQL("ALTER TABLE volunteers ADD COLUMN gender TEXT")
                    
                    println("Successfully added gender column to volunteers table")
                } catch (e: Exception) {
                    println("Migration 11_12 failed: ${e.message}")
                    throw e
                }
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Create venues table
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS venues (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            sheetsId TEXT,
                            name TEXT NOT NULL,
                            description TEXT NOT NULL DEFAULT '',
                            isActive INTEGER NOT NULL DEFAULT 1,
                            lastModified INTEGER NOT NULL
                        )
                    """)
                    
                    // Insert default venues
                    val currentTime = System.currentTimeMillis()
                    val defaultVenues = listOf(
                        "GROOVE" to "Main venue for events",
                        "LE_TERREAU" to "Secondary venue for events"
                    )
                    
                    defaultVenues.forEach { (name, description) ->
                        db.execSQL("""
                            INSERT INTO venues (name, description, isActive, lastModified)
                            VALUES ('$name', '$description', 1, $currentTime)
                        """)
                    }
                    
                    println("Successfully created venues table with default venues")
                } catch (e: Exception) {
                    println("Migration 12_13 failed: ${e.message}")
                    throw e
                }
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Check if venues table exists and what its current schema is
                    val cursor = db.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='venues'")
                    val tableExists = cursor.moveToFirst()
                    cursor.close()
                    
                    if (tableExists) {
                        // Table exists, check if it needs schema updates
                        try {
                            // Try to query for sheetsId column to see if it exists
                            val testCursor = db.query("SELECT sheetsId FROM venues LIMIT 1")
                            testCursor.close()
                            println("venues table already has correct schema")
                        } catch (e: Exception) {
                            // sheetsId column doesn't exist, need to add it
                            println("Adding sheetsId column to venues table")
                            db.execSQL("ALTER TABLE venues ADD COLUMN sheetsId TEXT")
                        }
                        
                        // Check if isDiscovered column exists and remove it if it does
                        try {
                            val testCursor = db.query("SELECT isDiscovered FROM venues LIMIT 1")
                            testCursor.close()
                            // Column exists, we need to recreate the table without it
                            println("Removing isDiscovered column from venues table")
                            
                            // Create new table with correct schema
                            db.execSQL("""
                                CREATE TABLE venues_new (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                    sheetsId TEXT,
                                    name TEXT NOT NULL,
                                    description TEXT NOT NULL DEFAULT '',
                                    isActive INTEGER NOT NULL DEFAULT 1,
                                    lastModified INTEGER NOT NULL
                                )
                            """)
                            
                            // Copy data from old table to new table
                            db.execSQL("""
                                INSERT INTO venues_new (id, sheetsId, name, description, isActive, lastModified)
                                SELECT id, sheetsId, name, description, isActive, lastModified FROM venues
                            """)
                            
                            // Drop old table and rename new one
                            db.execSQL("DROP TABLE venues")
                            db.execSQL("ALTER TABLE venues_new RENAME TO venues")
                            
                        } catch (e: Exception) {
                            // isDiscovered column doesn't exist, that's fine
                            println("isDiscovered column doesn't exist, schema is correct")
                        }
                    } else {
                        // Table doesn't exist, create it
                        println("Creating venues table")
                        db.execSQL("""
                            CREATE TABLE venues (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                sheetsId TEXT,
                                name TEXT NOT NULL,
                                description TEXT NOT NULL DEFAULT '',
                                isActive INTEGER NOT NULL DEFAULT 1,
                                lastModified INTEGER NOT NULL
                            )
                        """)
                    }
                    
                    // Check if venues table is empty and add default venues if needed
                    val countCursor = db.query("SELECT COUNT(*) FROM venues")
                    val count = if (countCursor.moveToFirst()) countCursor.getInt(0) else 0
                    countCursor.close()
                    
                    if (count == 0) {
                        val currentTime = System.currentTimeMillis()
                        val defaultVenues = listOf(
                            "GROOVE" to "Main venue for events",
                            "LE_TERREAU" to "Secondary venue for events"
                        )
                        
                        defaultVenues.forEach { (name, description) ->
                            db.execSQL("""
                                INSERT INTO venues (name, description, isActive, lastModified)
                                VALUES ('$name', '$description', 1, $currentTime)
                            """)
                        }
                        println("Added default venues to empty venues table")
                    }
                    
                    println("Migration 13_14 completed successfully")
                } catch (e: Exception) {
                    println("Migration 13_14 failed: ${e.message}")
                    throw e
                }
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Migrate guests table: rename old table, create new one, migrate data, drop old
                    db.execSQL("ALTER TABLE guests RENAME TO guests_old")
                    db.execSQL("""
                        CREATE TABLE guests (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            invitations INTEGER NOT NULL,
                            venueName TEXT NOT NULL DEFAULT 'OTHER',
                            notes TEXT NOT NULL,
                            isVolunteerBenefit INTEGER NOT NULL,
                            lastModified INTEGER NOT NULL,
                            sheetsId TEXT,
                            volunteerId INTEGER,
                            lastNameAbbreviation TEXT NOT NULL
                        )
                    """)
                    db.execSQL("""
                        INSERT INTO guests (id, name, invitations, venueName, notes, isVolunteerBenefit, lastModified, sheetsId, volunteerId, lastNameAbbreviation)
                        SELECT id, name, invitations, 
                            CASE venue
                                WHEN 'GROOVE' THEN 'GROOVE'
                                WHEN 'LE_TERREAU' THEN 'LE_TERREAU'
                                WHEN 'BOTH' THEN 'BOTH'
                                ELSE 'OTHER'
                            END as venueName,
                            notes, isVolunteerBenefit, lastModified, sheetsId, volunteerId, lastNameAbbreviation
                        FROM guests_old
                    """)
                    db.execSQL("DROP TABLE guests_old")
                    
                    // Migrate jobs table: rename old table, create new one, migrate data, drop old
                    db.execSQL("ALTER TABLE jobs RENAME TO jobs_old")
                    db.execSQL("""
                        CREATE TABLE jobs (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            volunteerId INTEGER NOT NULL,
                            date INTEGER NOT NULL,
                            shiftTime TEXT NOT NULL,
                            jobType TEXT NOT NULL,
                            jobTypeName TEXT NOT NULL,
                            venueName TEXT NOT NULL DEFAULT 'OTHER',
                            notes TEXT NOT NULL,
                            lastModified INTEGER NOT NULL,
                            sheetsId TEXT
                        )
                    """)
                    db.execSQL("""
                        INSERT INTO jobs (id, volunteerId, date, shiftTime, jobType, jobTypeName, venueName, notes, lastModified, sheetsId)
                        SELECT id, volunteerId, date, shiftTime, jobType, jobTypeName,
                            CASE venue
                                WHEN 'GROOVE' THEN 'GROOVE'
                                WHEN 'LE_TERREAU' THEN 'LE_TERREAU'
                                WHEN 'BOTH' THEN 'BOTH'
                                ELSE 'OTHER'
                            END as venueName,
                            notes, lastModified, sheetsId
                        FROM jobs_old
                    """)
                    db.execSQL("DROP TABLE jobs_old")
                    println("Successfully converted venue enum to venueName string in both guests and jobs tables")
                } catch (e: Exception) {
                    println("Migration 14_15 failed: ${e.message}")
                    throw e
                }
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // This migration handles cleanup for databases that had incomplete v15 migration
                    // Check if guests table still has the old venue column
                    val guestsCursor = db.query("PRAGMA table_info(guests)")
                    var hasOldVenueColumn = false
                    while (guestsCursor.moveToNext()) {
                        val columnName = guestsCursor.getString(1)
                        if (columnName == "venue") {
                            hasOldVenueColumn = true
                            break
                        }
                    }
                    guestsCursor.close()

                    if (hasOldVenueColumn) {
                        // Recreate guests table without old venue column
                        db.execSQL("ALTER TABLE guests RENAME TO guests_old")
                        db.execSQL("""
                            CREATE TABLE guests (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                name TEXT NOT NULL,
                                invitations INTEGER NOT NULL,
                                venueName TEXT NOT NULL DEFAULT 'OTHER',
                                notes TEXT NOT NULL,
                                isVolunteerBenefit INTEGER NOT NULL,
                                lastModified INTEGER NOT NULL,
                                sheetsId TEXT,
                                volunteerId INTEGER,
                                lastNameAbbreviation TEXT NOT NULL
                            )
                        """)
                        db.execSQL("""
                            INSERT INTO guests (id, name, invitations, venueName, notes, isVolunteerBenefit, lastModified, sheetsId, volunteerId, lastNameAbbreviation)
                            SELECT id, name, invitations, 
                                CASE WHEN venueName IS NOT NULL AND venueName != '' THEN venueName
                                     ELSE CASE venue
                                        WHEN 'GROOVE' THEN 'GROOVE'
                                        WHEN 'LE_TERREAU' THEN 'LE_TERREAU'
                                        WHEN 'BOTH' THEN 'BOTH'
                                        ELSE 'OTHER'
                                     END
                                END as venueName,
                                notes, isVolunteerBenefit, lastModified, sheetsId, volunteerId, lastNameAbbreviation
                            FROM guests_old
                        """)
                        db.execSQL("DROP TABLE guests_old")
                        
                        // Recreate jobs table without old venue column
                        db.execSQL("ALTER TABLE jobs RENAME TO jobs_old")
                        db.execSQL("""
                            CREATE TABLE jobs (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                volunteerId INTEGER NOT NULL,
                                date INTEGER NOT NULL,
                                shiftTime TEXT NOT NULL,
                                jobType TEXT NOT NULL,
                                jobTypeName TEXT NOT NULL,
                                venueName TEXT NOT NULL DEFAULT 'OTHER',
                                notes TEXT NOT NULL,
                                lastModified INTEGER NOT NULL,
                                sheetsId TEXT
                            )
                        """)
                        db.execSQL("""
                            INSERT INTO jobs (id, volunteerId, date, shiftTime, jobType, jobTypeName, venueName, notes, lastModified, sheetsId)
                            SELECT id, volunteerId, date, shiftTime, jobType, jobTypeName,
                                CASE WHEN venueName IS NOT NULL AND venueName != '' THEN venueName
                                     ELSE CASE venue
                                        WHEN 'GROOVE' THEN 'GROOVE'
                                        WHEN 'LE_TERREAU' THEN 'LE_TERREAU'
                                        WHEN 'BOTH' THEN 'BOTH'
                                        ELSE 'OTHER'
                                     END
                                END as venueName,
                                notes, lastModified, sheetsId
                            FROM jobs_old
                        """)
                        db.execSQL("DROP TABLE jobs_old")
                        println("Migration 15_16: Cleaned up old venue column from guests and jobs tables")
                    } else {
                        println("Migration 15_16: Tables already in correct state, skipping cleanup")
                    }
                } catch (e: Exception) {
                    println("Migration 15_16 warning: ${e.message}")
                    // Don't throw - this is a cleanup migration
                }
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Create people_counter table
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS people_counter (
                            id INTEGER PRIMARY KEY NOT NULL,
                            count INTEGER NOT NULL DEFAULT 0,
                            lastModified INTEGER NOT NULL
                        )
                    """)
                    // Insert default counter if doesn't exist
                    db.execSQL("""
                        INSERT OR IGNORE INTO people_counter (id, count, lastModified)
                        VALUES (1, 0, ${System.currentTimeMillis()})
                    """)
                    println("Successfully created people_counter table")
                } catch (e: Exception) {
                    println("Migration 16_17 failed: ${e.message}")
                    throw e
                }
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Add indices for guests table
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_guests_sheetsId ON guests(sheetsId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_guests_volunteerId ON guests(volunteerId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_guests_venueName ON guests(venueName)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_guests_lastModified ON guests(lastModified)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_guests_isVolunteerBenefit ON guests(isVolunteerBenefit)")
                    
                    // Add indices for volunteers table
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_volunteers_sheetsId ON volunteers(sheetsId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_volunteers_isActive ON volunteers(isActive)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_volunteers_currentRank ON volunteers(currentRank)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_volunteers_lastModified ON volunteers(lastModified)")
                    
                    // Add indices for jobs table
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_volunteerId ON jobs(volunteerId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_date ON jobs(date)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_venueName ON jobs(venueName)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_jobTypeName ON jobs(jobTypeName)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_sheetsId ON jobs(sheetsId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_lastModified ON jobs(lastModified)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_volunteerId_date ON jobs(volunteerId, date)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_date_shiftTime ON jobs(date, shiftTime)")
                    
                    // Add indices for job_type_configs table
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_job_type_configs_name ON job_type_configs(name)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_job_type_configs_sheetsId ON job_type_configs(sheetsId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_job_type_configs_isActive ON job_type_configs(isActive)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_job_type_configs_lastModified ON job_type_configs(lastModified)")
                    
                    // Add indices for venues table
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_venues_name ON venues(name)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_venues_sheetsId ON venues(sheetsId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_venues_isActive ON venues(isActive)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_venues_lastModified ON venues(lastModified)")
                    
                    println("Successfully added database indices in migration 17_18")
                } catch (e: Exception) {
                    println("Migration 17_18 failed: ${e.message}")
                    throw e
                }
            }
        }

        fun getDatabase(context: Context): EventManagerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EventManagerDatabase::class.java,
                    "event_manager_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        fun clearDatabase(context: Context) {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                // Delete the database file to force recreation
                val dbFile = context.getDatabasePath("event_manager_database")
                if (dbFile.exists()) {
                    dbFile.delete()
                    println("Deleted existing database file to force recreation")
                }
            }
        }
    }
}

