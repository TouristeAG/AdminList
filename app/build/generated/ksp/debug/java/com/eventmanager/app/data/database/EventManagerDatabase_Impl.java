package com.eventmanager.app.data.database;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.eventmanager.app.data.dao.CounterDao;
import com.eventmanager.app.data.dao.CounterDao_Impl;
import com.eventmanager.app.data.dao.GuestDao;
import com.eventmanager.app.data.dao.GuestDao_Impl;
import com.eventmanager.app.data.dao.JobDao;
import com.eventmanager.app.data.dao.JobDao_Impl;
import com.eventmanager.app.data.dao.JobTypeConfigDao;
import com.eventmanager.app.data.dao.JobTypeConfigDao_Impl;
import com.eventmanager.app.data.dao.VenueDao;
import com.eventmanager.app.data.dao.VenueDao_Impl;
import com.eventmanager.app.data.dao.VolunteerDao;
import com.eventmanager.app.data.dao.VolunteerDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class EventManagerDatabase_Impl extends EventManagerDatabase {
  private volatile GuestDao _guestDao;

  private volatile VolunteerDao _volunteerDao;

  private volatile JobDao _jobDao;

  private volatile JobTypeConfigDao _jobTypeConfigDao;

  private volatile VenueDao _venueDao;

  private volatile CounterDao _counterDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(18) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `guests` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sheetsId` TEXT, `name` TEXT NOT NULL, `lastNameAbbreviation` TEXT NOT NULL, `invitations` INTEGER NOT NULL, `venueName` TEXT NOT NULL, `notes` TEXT NOT NULL, `isVolunteerBenefit` INTEGER NOT NULL, `volunteerId` INTEGER, `lastModified` INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_guests_sheetsId` ON `guests` (`sheetsId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_guests_volunteerId` ON `guests` (`volunteerId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_guests_venueName` ON `guests` (`venueName`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_guests_lastModified` ON `guests` (`lastModified`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_guests_isVolunteerBenefit` ON `guests` (`isVolunteerBenefit`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `volunteers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sheetsId` TEXT, `name` TEXT NOT NULL, `lastNameAbbreviation` TEXT NOT NULL, `email` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `dateOfBirth` TEXT NOT NULL, `gender` TEXT, `currentRank` TEXT, `isActive` INTEGER NOT NULL, `lastShiftDate` INTEGER, `lastModified` INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_volunteers_sheetsId` ON `volunteers` (`sheetsId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_volunteers_isActive` ON `volunteers` (`isActive`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_volunteers_currentRank` ON `volunteers` (`currentRank`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_volunteers_lastModified` ON `volunteers` (`lastModified`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `jobs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sheetsId` TEXT, `volunteerId` INTEGER NOT NULL, `jobType` TEXT NOT NULL, `jobTypeName` TEXT NOT NULL, `venueName` TEXT NOT NULL, `date` INTEGER NOT NULL, `shiftTime` TEXT NOT NULL, `notes` TEXT NOT NULL, `lastModified` INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_jobs_volunteerId` ON `jobs` (`volunteerId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_jobs_date` ON `jobs` (`date`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_jobs_venueName` ON `jobs` (`venueName`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_jobs_jobTypeName` ON `jobs` (`jobTypeName`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_jobs_sheetsId` ON `jobs` (`sheetsId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_jobs_lastModified` ON `jobs` (`lastModified`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_jobs_volunteerId_date` ON `jobs` (`volunteerId`, `date`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_jobs_date_shiftTime` ON `jobs` (`date`, `shiftTime`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `job_type_configs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sheetsId` TEXT, `name` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `isShiftJob` INTEGER NOT NULL, `isOrionJob` INTEGER NOT NULL, `requiresShiftTime` INTEGER NOT NULL, `benefitSystemType` TEXT NOT NULL, `manualRewards` TEXT, `description` TEXT NOT NULL, `lastModified` INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_job_type_configs_sheetsId` ON `job_type_configs` (`sheetsId`)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_job_type_configs_name` ON `job_type_configs` (`name`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_job_type_configs_isActive` ON `job_type_configs` (`isActive`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_job_type_configs_lastModified` ON `job_type_configs` (`lastModified`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `venues` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sheetsId` TEXT, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `lastModified` INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_venues_sheetsId` ON `venues` (`sheetsId`)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_venues_name` ON `venues` (`name`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_venues_isActive` ON `venues` (`isActive`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_venues_lastModified` ON `venues` (`lastModified`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `people_counter` (`id` INTEGER NOT NULL, `count` INTEGER NOT NULL, `lastModified` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '1a6bbbfb3f742296c4aae75083dbd54e')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `guests`");
        db.execSQL("DROP TABLE IF EXISTS `volunteers`");
        db.execSQL("DROP TABLE IF EXISTS `jobs`");
        db.execSQL("DROP TABLE IF EXISTS `job_type_configs`");
        db.execSQL("DROP TABLE IF EXISTS `venues`");
        db.execSQL("DROP TABLE IF EXISTS `people_counter`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsGuests = new HashMap<String, TableInfo.Column>(10);
        _columnsGuests.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGuests.put("sheetsId", new TableInfo.Column("sheetsId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGuests.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGuests.put("lastNameAbbreviation", new TableInfo.Column("lastNameAbbreviation", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGuests.put("invitations", new TableInfo.Column("invitations", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGuests.put("venueName", new TableInfo.Column("venueName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGuests.put("notes", new TableInfo.Column("notes", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGuests.put("isVolunteerBenefit", new TableInfo.Column("isVolunteerBenefit", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGuests.put("volunteerId", new TableInfo.Column("volunteerId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGuests.put("lastModified", new TableInfo.Column("lastModified", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysGuests = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesGuests = new HashSet<TableInfo.Index>(5);
        _indicesGuests.add(new TableInfo.Index("index_guests_sheetsId", false, Arrays.asList("sheetsId"), Arrays.asList("ASC")));
        _indicesGuests.add(new TableInfo.Index("index_guests_volunteerId", false, Arrays.asList("volunteerId"), Arrays.asList("ASC")));
        _indicesGuests.add(new TableInfo.Index("index_guests_venueName", false, Arrays.asList("venueName"), Arrays.asList("ASC")));
        _indicesGuests.add(new TableInfo.Index("index_guests_lastModified", false, Arrays.asList("lastModified"), Arrays.asList("ASC")));
        _indicesGuests.add(new TableInfo.Index("index_guests_isVolunteerBenefit", false, Arrays.asList("isVolunteerBenefit"), Arrays.asList("ASC")));
        final TableInfo _infoGuests = new TableInfo("guests", _columnsGuests, _foreignKeysGuests, _indicesGuests);
        final TableInfo _existingGuests = TableInfo.read(db, "guests");
        if (!_infoGuests.equals(_existingGuests)) {
          return new RoomOpenHelper.ValidationResult(false, "guests(com.eventmanager.app.data.models.Guest).\n"
                  + " Expected:\n" + _infoGuests + "\n"
                  + " Found:\n" + _existingGuests);
        }
        final HashMap<String, TableInfo.Column> _columnsVolunteers = new HashMap<String, TableInfo.Column>(12);
        _columnsVolunteers.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVolunteers.put("sheetsId", new TableInfo.Column("sheetsId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVolunteers.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVolunteers.put("lastNameAbbreviation", new TableInfo.Column("lastNameAbbreviation", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVolunteers.put("email", new TableInfo.Column("email", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVolunteers.put("phoneNumber", new TableInfo.Column("phoneNumber", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVolunteers.put("dateOfBirth", new TableInfo.Column("dateOfBirth", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVolunteers.put("gender", new TableInfo.Column("gender", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVolunteers.put("currentRank", new TableInfo.Column("currentRank", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVolunteers.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVolunteers.put("lastShiftDate", new TableInfo.Column("lastShiftDate", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVolunteers.put("lastModified", new TableInfo.Column("lastModified", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysVolunteers = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesVolunteers = new HashSet<TableInfo.Index>(4);
        _indicesVolunteers.add(new TableInfo.Index("index_volunteers_sheetsId", false, Arrays.asList("sheetsId"), Arrays.asList("ASC")));
        _indicesVolunteers.add(new TableInfo.Index("index_volunteers_isActive", false, Arrays.asList("isActive"), Arrays.asList("ASC")));
        _indicesVolunteers.add(new TableInfo.Index("index_volunteers_currentRank", false, Arrays.asList("currentRank"), Arrays.asList("ASC")));
        _indicesVolunteers.add(new TableInfo.Index("index_volunteers_lastModified", false, Arrays.asList("lastModified"), Arrays.asList("ASC")));
        final TableInfo _infoVolunteers = new TableInfo("volunteers", _columnsVolunteers, _foreignKeysVolunteers, _indicesVolunteers);
        final TableInfo _existingVolunteers = TableInfo.read(db, "volunteers");
        if (!_infoVolunteers.equals(_existingVolunteers)) {
          return new RoomOpenHelper.ValidationResult(false, "volunteers(com.eventmanager.app.data.models.Volunteer).\n"
                  + " Expected:\n" + _infoVolunteers + "\n"
                  + " Found:\n" + _existingVolunteers);
        }
        final HashMap<String, TableInfo.Column> _columnsJobs = new HashMap<String, TableInfo.Column>(10);
        _columnsJobs.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobs.put("sheetsId", new TableInfo.Column("sheetsId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobs.put("volunteerId", new TableInfo.Column("volunteerId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobs.put("jobType", new TableInfo.Column("jobType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobs.put("jobTypeName", new TableInfo.Column("jobTypeName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobs.put("venueName", new TableInfo.Column("venueName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobs.put("date", new TableInfo.Column("date", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobs.put("shiftTime", new TableInfo.Column("shiftTime", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobs.put("notes", new TableInfo.Column("notes", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobs.put("lastModified", new TableInfo.Column("lastModified", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysJobs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesJobs = new HashSet<TableInfo.Index>(8);
        _indicesJobs.add(new TableInfo.Index("index_jobs_volunteerId", false, Arrays.asList("volunteerId"), Arrays.asList("ASC")));
        _indicesJobs.add(new TableInfo.Index("index_jobs_date", false, Arrays.asList("date"), Arrays.asList("ASC")));
        _indicesJobs.add(new TableInfo.Index("index_jobs_venueName", false, Arrays.asList("venueName"), Arrays.asList("ASC")));
        _indicesJobs.add(new TableInfo.Index("index_jobs_jobTypeName", false, Arrays.asList("jobTypeName"), Arrays.asList("ASC")));
        _indicesJobs.add(new TableInfo.Index("index_jobs_sheetsId", false, Arrays.asList("sheetsId"), Arrays.asList("ASC")));
        _indicesJobs.add(new TableInfo.Index("index_jobs_lastModified", false, Arrays.asList("lastModified"), Arrays.asList("ASC")));
        _indicesJobs.add(new TableInfo.Index("index_jobs_volunteerId_date", false, Arrays.asList("volunteerId", "date"), Arrays.asList("ASC", "ASC")));
        _indicesJobs.add(new TableInfo.Index("index_jobs_date_shiftTime", false, Arrays.asList("date", "shiftTime"), Arrays.asList("ASC", "ASC")));
        final TableInfo _infoJobs = new TableInfo("jobs", _columnsJobs, _foreignKeysJobs, _indicesJobs);
        final TableInfo _existingJobs = TableInfo.read(db, "jobs");
        if (!_infoJobs.equals(_existingJobs)) {
          return new RoomOpenHelper.ValidationResult(false, "jobs(com.eventmanager.app.data.models.Job).\n"
                  + " Expected:\n" + _infoJobs + "\n"
                  + " Found:\n" + _existingJobs);
        }
        final HashMap<String, TableInfo.Column> _columnsJobTypeConfigs = new HashMap<String, TableInfo.Column>(11);
        _columnsJobTypeConfigs.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobTypeConfigs.put("sheetsId", new TableInfo.Column("sheetsId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobTypeConfigs.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobTypeConfigs.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobTypeConfigs.put("isShiftJob", new TableInfo.Column("isShiftJob", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobTypeConfigs.put("isOrionJob", new TableInfo.Column("isOrionJob", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobTypeConfigs.put("requiresShiftTime", new TableInfo.Column("requiresShiftTime", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobTypeConfigs.put("benefitSystemType", new TableInfo.Column("benefitSystemType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobTypeConfigs.put("manualRewards", new TableInfo.Column("manualRewards", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobTypeConfigs.put("description", new TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsJobTypeConfigs.put("lastModified", new TableInfo.Column("lastModified", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysJobTypeConfigs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesJobTypeConfigs = new HashSet<TableInfo.Index>(4);
        _indicesJobTypeConfigs.add(new TableInfo.Index("index_job_type_configs_sheetsId", false, Arrays.asList("sheetsId"), Arrays.asList("ASC")));
        _indicesJobTypeConfigs.add(new TableInfo.Index("index_job_type_configs_name", true, Arrays.asList("name"), Arrays.asList("ASC")));
        _indicesJobTypeConfigs.add(new TableInfo.Index("index_job_type_configs_isActive", false, Arrays.asList("isActive"), Arrays.asList("ASC")));
        _indicesJobTypeConfigs.add(new TableInfo.Index("index_job_type_configs_lastModified", false, Arrays.asList("lastModified"), Arrays.asList("ASC")));
        final TableInfo _infoJobTypeConfigs = new TableInfo("job_type_configs", _columnsJobTypeConfigs, _foreignKeysJobTypeConfigs, _indicesJobTypeConfigs);
        final TableInfo _existingJobTypeConfigs = TableInfo.read(db, "job_type_configs");
        if (!_infoJobTypeConfigs.equals(_existingJobTypeConfigs)) {
          return new RoomOpenHelper.ValidationResult(false, "job_type_configs(com.eventmanager.app.data.models.JobTypeConfig).\n"
                  + " Expected:\n" + _infoJobTypeConfigs + "\n"
                  + " Found:\n" + _existingJobTypeConfigs);
        }
        final HashMap<String, TableInfo.Column> _columnsVenues = new HashMap<String, TableInfo.Column>(6);
        _columnsVenues.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVenues.put("sheetsId", new TableInfo.Column("sheetsId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVenues.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVenues.put("description", new TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVenues.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVenues.put("lastModified", new TableInfo.Column("lastModified", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysVenues = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesVenues = new HashSet<TableInfo.Index>(4);
        _indicesVenues.add(new TableInfo.Index("index_venues_sheetsId", false, Arrays.asList("sheetsId"), Arrays.asList("ASC")));
        _indicesVenues.add(new TableInfo.Index("index_venues_name", true, Arrays.asList("name"), Arrays.asList("ASC")));
        _indicesVenues.add(new TableInfo.Index("index_venues_isActive", false, Arrays.asList("isActive"), Arrays.asList("ASC")));
        _indicesVenues.add(new TableInfo.Index("index_venues_lastModified", false, Arrays.asList("lastModified"), Arrays.asList("ASC")));
        final TableInfo _infoVenues = new TableInfo("venues", _columnsVenues, _foreignKeysVenues, _indicesVenues);
        final TableInfo _existingVenues = TableInfo.read(db, "venues");
        if (!_infoVenues.equals(_existingVenues)) {
          return new RoomOpenHelper.ValidationResult(false, "venues(com.eventmanager.app.data.models.VenueEntity).\n"
                  + " Expected:\n" + _infoVenues + "\n"
                  + " Found:\n" + _existingVenues);
        }
        final HashMap<String, TableInfo.Column> _columnsPeopleCounter = new HashMap<String, TableInfo.Column>(3);
        _columnsPeopleCounter.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPeopleCounter.put("count", new TableInfo.Column("count", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPeopleCounter.put("lastModified", new TableInfo.Column("lastModified", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPeopleCounter = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPeopleCounter = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPeopleCounter = new TableInfo("people_counter", _columnsPeopleCounter, _foreignKeysPeopleCounter, _indicesPeopleCounter);
        final TableInfo _existingPeopleCounter = TableInfo.read(db, "people_counter");
        if (!_infoPeopleCounter.equals(_existingPeopleCounter)) {
          return new RoomOpenHelper.ValidationResult(false, "people_counter(com.eventmanager.app.data.models.CounterData).\n"
                  + " Expected:\n" + _infoPeopleCounter + "\n"
                  + " Found:\n" + _existingPeopleCounter);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "1a6bbbfb3f742296c4aae75083dbd54e", "13fd248b557f54763e766c3cbebdfe84");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "guests","volunteers","jobs","job_type_configs","venues","people_counter");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `guests`");
      _db.execSQL("DELETE FROM `volunteers`");
      _db.execSQL("DELETE FROM `jobs`");
      _db.execSQL("DELETE FROM `job_type_configs`");
      _db.execSQL("DELETE FROM `venues`");
      _db.execSQL("DELETE FROM `people_counter`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(GuestDao.class, GuestDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(VolunteerDao.class, VolunteerDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(JobDao.class, JobDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(JobTypeConfigDao.class, JobTypeConfigDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(VenueDao.class, VenueDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CounterDao.class, CounterDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public GuestDao guestDao() {
    if (_guestDao != null) {
      return _guestDao;
    } else {
      synchronized(this) {
        if(_guestDao == null) {
          _guestDao = new GuestDao_Impl(this);
        }
        return _guestDao;
      }
    }
  }

  @Override
  public VolunteerDao volunteerDao() {
    if (_volunteerDao != null) {
      return _volunteerDao;
    } else {
      synchronized(this) {
        if(_volunteerDao == null) {
          _volunteerDao = new VolunteerDao_Impl(this);
        }
        return _volunteerDao;
      }
    }
  }

  @Override
  public JobDao jobDao() {
    if (_jobDao != null) {
      return _jobDao;
    } else {
      synchronized(this) {
        if(_jobDao == null) {
          _jobDao = new JobDao_Impl(this);
        }
        return _jobDao;
      }
    }
  }

  @Override
  public JobTypeConfigDao jobTypeConfigDao() {
    if (_jobTypeConfigDao != null) {
      return _jobTypeConfigDao;
    } else {
      synchronized(this) {
        if(_jobTypeConfigDao == null) {
          _jobTypeConfigDao = new JobTypeConfigDao_Impl(this);
        }
        return _jobTypeConfigDao;
      }
    }
  }

  @Override
  public VenueDao venueDao() {
    if (_venueDao != null) {
      return _venueDao;
    } else {
      synchronized(this) {
        if(_venueDao == null) {
          _venueDao = new VenueDao_Impl(this);
        }
        return _venueDao;
      }
    }
  }

  @Override
  public CounterDao counterDao() {
    if (_counterDao != null) {
      return _counterDao;
    } else {
      synchronized(this) {
        if(_counterDao == null) {
          _counterDao = new CounterDao_Impl(this);
        }
        return _counterDao;
      }
    }
  }
}
