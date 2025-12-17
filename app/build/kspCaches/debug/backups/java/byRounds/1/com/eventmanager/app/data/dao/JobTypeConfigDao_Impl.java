package com.eventmanager.app.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.eventmanager.app.data.models.BenefitSystemType;
import com.eventmanager.app.data.models.Converters;
import com.eventmanager.app.data.models.JobTypeConfig;
import com.eventmanager.app.data.models.ManualRewards;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class JobTypeConfigDao_Impl implements JobTypeConfigDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<JobTypeConfig> __insertionAdapterOfJobTypeConfig;

  private final Converters __converters = new Converters();

  private final EntityDeletionOrUpdateAdapter<JobTypeConfig> __deletionAdapterOfJobTypeConfig;

  private final EntityDeletionOrUpdateAdapter<JobTypeConfig> __updateAdapterOfJobTypeConfig;

  private final SharedSQLiteStatement __preparedStmtOfDeleteJobTypeConfigById;

  private final SharedSQLiteStatement __preparedStmtOfUpdateJobTypeConfigStatus;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllJobTypeConfigs;

  public JobTypeConfigDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfJobTypeConfig = new EntityInsertionAdapter<JobTypeConfig>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `job_type_configs` (`id`,`sheetsId`,`name`,`isActive`,`isShiftJob`,`isOrionJob`,`requiresShiftTime`,`benefitSystemType`,`manualRewards`,`description`,`lastModified`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final JobTypeConfig entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getSheetsId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getSheetsId());
        }
        statement.bindString(3, entity.getName());
        final int _tmp = entity.isActive() ? 1 : 0;
        statement.bindLong(4, _tmp);
        final int _tmp_1 = entity.isShiftJob() ? 1 : 0;
        statement.bindLong(5, _tmp_1);
        final int _tmp_2 = entity.isOrionJob() ? 1 : 0;
        statement.bindLong(6, _tmp_2);
        final int _tmp_3 = entity.getRequiresShiftTime() ? 1 : 0;
        statement.bindLong(7, _tmp_3);
        final String _tmp_4 = __converters.fromBenefitSystemType(entity.getBenefitSystemType());
        statement.bindString(8, _tmp_4);
        final String _tmp_5 = __converters.fromManualRewards(entity.getManualRewards());
        if (_tmp_5 == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, _tmp_5);
        }
        statement.bindString(10, entity.getDescription());
        statement.bindLong(11, entity.getLastModified());
      }
    };
    this.__deletionAdapterOfJobTypeConfig = new EntityDeletionOrUpdateAdapter<JobTypeConfig>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `job_type_configs` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final JobTypeConfig entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfJobTypeConfig = new EntityDeletionOrUpdateAdapter<JobTypeConfig>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `job_type_configs` SET `id` = ?,`sheetsId` = ?,`name` = ?,`isActive` = ?,`isShiftJob` = ?,`isOrionJob` = ?,`requiresShiftTime` = ?,`benefitSystemType` = ?,`manualRewards` = ?,`description` = ?,`lastModified` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final JobTypeConfig entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getSheetsId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getSheetsId());
        }
        statement.bindString(3, entity.getName());
        final int _tmp = entity.isActive() ? 1 : 0;
        statement.bindLong(4, _tmp);
        final int _tmp_1 = entity.isShiftJob() ? 1 : 0;
        statement.bindLong(5, _tmp_1);
        final int _tmp_2 = entity.isOrionJob() ? 1 : 0;
        statement.bindLong(6, _tmp_2);
        final int _tmp_3 = entity.getRequiresShiftTime() ? 1 : 0;
        statement.bindLong(7, _tmp_3);
        final String _tmp_4 = __converters.fromBenefitSystemType(entity.getBenefitSystemType());
        statement.bindString(8, _tmp_4);
        final String _tmp_5 = __converters.fromManualRewards(entity.getManualRewards());
        if (_tmp_5 == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, _tmp_5);
        }
        statement.bindString(10, entity.getDescription());
        statement.bindLong(11, entity.getLastModified());
        statement.bindLong(12, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteJobTypeConfigById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM job_type_configs WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateJobTypeConfigStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE job_type_configs SET isActive = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllJobTypeConfigs = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM job_type_configs";
        return _query;
      }
    };
  }

  @Override
  public Object insertJobTypeConfig(final JobTypeConfig config,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfJobTypeConfig.insertAndReturnId(config);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteJobTypeConfig(final JobTypeConfig config,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfJobTypeConfig.handle(config);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateJobTypeConfig(final JobTypeConfig config,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfJobTypeConfig.handle(config);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteJobTypeConfigById(final long id,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteJobTypeConfigById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteJobTypeConfigById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateJobTypeConfigStatus(final long id, final boolean isActive,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateJobTypeConfigStatus.acquire();
        int _argIndex = 1;
        final int _tmp = isActive ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateJobTypeConfigStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllJobTypeConfigs(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllJobTypeConfigs.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAllJobTypeConfigs.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<JobTypeConfig>> getAllActiveJobTypeConfigs() {
    final String _sql = "SELECT * FROM job_type_configs WHERE isActive = 1 ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"job_type_configs"}, new Callable<List<JobTypeConfig>>() {
      @Override
      @NonNull
      public List<JobTypeConfig> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfIsShiftJob = CursorUtil.getColumnIndexOrThrow(_cursor, "isShiftJob");
          final int _cursorIndexOfIsOrionJob = CursorUtil.getColumnIndexOrThrow(_cursor, "isOrionJob");
          final int _cursorIndexOfRequiresShiftTime = CursorUtil.getColumnIndexOrThrow(_cursor, "requiresShiftTime");
          final int _cursorIndexOfBenefitSystemType = CursorUtil.getColumnIndexOrThrow(_cursor, "benefitSystemType");
          final int _cursorIndexOfManualRewards = CursorUtil.getColumnIndexOrThrow(_cursor, "manualRewards");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<JobTypeConfig> _result = new ArrayList<JobTypeConfig>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final JobTypeConfig _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSheetsId;
            if (_cursor.isNull(_cursorIndexOfSheetsId)) {
              _tmpSheetsId = null;
            } else {
              _tmpSheetsId = _cursor.getString(_cursorIndexOfSheetsId);
            }
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final boolean _tmpIsShiftJob;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsShiftJob);
            _tmpIsShiftJob = _tmp_1 != 0;
            final boolean _tmpIsOrionJob;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsOrionJob);
            _tmpIsOrionJob = _tmp_2 != 0;
            final boolean _tmpRequiresShiftTime;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfRequiresShiftTime);
            _tmpRequiresShiftTime = _tmp_3 != 0;
            final BenefitSystemType _tmpBenefitSystemType;
            final String _tmp_4;
            _tmp_4 = _cursor.getString(_cursorIndexOfBenefitSystemType);
            _tmpBenefitSystemType = __converters.toBenefitSystemType(_tmp_4);
            final ManualRewards _tmpManualRewards;
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfManualRewards)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfManualRewards);
            }
            _tmpManualRewards = __converters.toManualRewards(_tmp_5);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new JobTypeConfig(_tmpId,_tmpSheetsId,_tmpName,_tmpIsActive,_tmpIsShiftJob,_tmpIsOrionJob,_tmpRequiresShiftTime,_tmpBenefitSystemType,_tmpManualRewards,_tmpDescription,_tmpLastModified);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<JobTypeConfig>> getAllJobTypeConfigs() {
    final String _sql = "SELECT * FROM job_type_configs ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"job_type_configs"}, new Callable<List<JobTypeConfig>>() {
      @Override
      @NonNull
      public List<JobTypeConfig> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfIsShiftJob = CursorUtil.getColumnIndexOrThrow(_cursor, "isShiftJob");
          final int _cursorIndexOfIsOrionJob = CursorUtil.getColumnIndexOrThrow(_cursor, "isOrionJob");
          final int _cursorIndexOfRequiresShiftTime = CursorUtil.getColumnIndexOrThrow(_cursor, "requiresShiftTime");
          final int _cursorIndexOfBenefitSystemType = CursorUtil.getColumnIndexOrThrow(_cursor, "benefitSystemType");
          final int _cursorIndexOfManualRewards = CursorUtil.getColumnIndexOrThrow(_cursor, "manualRewards");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<JobTypeConfig> _result = new ArrayList<JobTypeConfig>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final JobTypeConfig _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSheetsId;
            if (_cursor.isNull(_cursorIndexOfSheetsId)) {
              _tmpSheetsId = null;
            } else {
              _tmpSheetsId = _cursor.getString(_cursorIndexOfSheetsId);
            }
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final boolean _tmpIsShiftJob;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsShiftJob);
            _tmpIsShiftJob = _tmp_1 != 0;
            final boolean _tmpIsOrionJob;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsOrionJob);
            _tmpIsOrionJob = _tmp_2 != 0;
            final boolean _tmpRequiresShiftTime;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfRequiresShiftTime);
            _tmpRequiresShiftTime = _tmp_3 != 0;
            final BenefitSystemType _tmpBenefitSystemType;
            final String _tmp_4;
            _tmp_4 = _cursor.getString(_cursorIndexOfBenefitSystemType);
            _tmpBenefitSystemType = __converters.toBenefitSystemType(_tmp_4);
            final ManualRewards _tmpManualRewards;
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfManualRewards)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfManualRewards);
            }
            _tmpManualRewards = __converters.toManualRewards(_tmp_5);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new JobTypeConfig(_tmpId,_tmpSheetsId,_tmpName,_tmpIsActive,_tmpIsShiftJob,_tmpIsOrionJob,_tmpRequiresShiftTime,_tmpBenefitSystemType,_tmpManualRewards,_tmpDescription,_tmpLastModified);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getJobTypeConfigById(final long id,
      final Continuation<? super JobTypeConfig> $completion) {
    final String _sql = "SELECT * FROM job_type_configs WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<JobTypeConfig>() {
      @Override
      @Nullable
      public JobTypeConfig call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfIsShiftJob = CursorUtil.getColumnIndexOrThrow(_cursor, "isShiftJob");
          final int _cursorIndexOfIsOrionJob = CursorUtil.getColumnIndexOrThrow(_cursor, "isOrionJob");
          final int _cursorIndexOfRequiresShiftTime = CursorUtil.getColumnIndexOrThrow(_cursor, "requiresShiftTime");
          final int _cursorIndexOfBenefitSystemType = CursorUtil.getColumnIndexOrThrow(_cursor, "benefitSystemType");
          final int _cursorIndexOfManualRewards = CursorUtil.getColumnIndexOrThrow(_cursor, "manualRewards");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final JobTypeConfig _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSheetsId;
            if (_cursor.isNull(_cursorIndexOfSheetsId)) {
              _tmpSheetsId = null;
            } else {
              _tmpSheetsId = _cursor.getString(_cursorIndexOfSheetsId);
            }
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final boolean _tmpIsShiftJob;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsShiftJob);
            _tmpIsShiftJob = _tmp_1 != 0;
            final boolean _tmpIsOrionJob;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsOrionJob);
            _tmpIsOrionJob = _tmp_2 != 0;
            final boolean _tmpRequiresShiftTime;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfRequiresShiftTime);
            _tmpRequiresShiftTime = _tmp_3 != 0;
            final BenefitSystemType _tmpBenefitSystemType;
            final String _tmp_4;
            _tmp_4 = _cursor.getString(_cursorIndexOfBenefitSystemType);
            _tmpBenefitSystemType = __converters.toBenefitSystemType(_tmp_4);
            final ManualRewards _tmpManualRewards;
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfManualRewards)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfManualRewards);
            }
            _tmpManualRewards = __converters.toManualRewards(_tmp_5);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _result = new JobTypeConfig(_tmpId,_tmpSheetsId,_tmpName,_tmpIsActive,_tmpIsShiftJob,_tmpIsOrionJob,_tmpRequiresShiftTime,_tmpBenefitSystemType,_tmpManualRewards,_tmpDescription,_tmpLastModified);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getJobTypeConfigByName(final String name,
      final Continuation<? super JobTypeConfig> $completion) {
    final String _sql = "SELECT * FROM job_type_configs WHERE name = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, name);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<JobTypeConfig>() {
      @Override
      @Nullable
      public JobTypeConfig call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfIsShiftJob = CursorUtil.getColumnIndexOrThrow(_cursor, "isShiftJob");
          final int _cursorIndexOfIsOrionJob = CursorUtil.getColumnIndexOrThrow(_cursor, "isOrionJob");
          final int _cursorIndexOfRequiresShiftTime = CursorUtil.getColumnIndexOrThrow(_cursor, "requiresShiftTime");
          final int _cursorIndexOfBenefitSystemType = CursorUtil.getColumnIndexOrThrow(_cursor, "benefitSystemType");
          final int _cursorIndexOfManualRewards = CursorUtil.getColumnIndexOrThrow(_cursor, "manualRewards");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final JobTypeConfig _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSheetsId;
            if (_cursor.isNull(_cursorIndexOfSheetsId)) {
              _tmpSheetsId = null;
            } else {
              _tmpSheetsId = _cursor.getString(_cursorIndexOfSheetsId);
            }
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final boolean _tmpIsShiftJob;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsShiftJob);
            _tmpIsShiftJob = _tmp_1 != 0;
            final boolean _tmpIsOrionJob;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsOrionJob);
            _tmpIsOrionJob = _tmp_2 != 0;
            final boolean _tmpRequiresShiftTime;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfRequiresShiftTime);
            _tmpRequiresShiftTime = _tmp_3 != 0;
            final BenefitSystemType _tmpBenefitSystemType;
            final String _tmp_4;
            _tmp_4 = _cursor.getString(_cursorIndexOfBenefitSystemType);
            _tmpBenefitSystemType = __converters.toBenefitSystemType(_tmp_4);
            final ManualRewards _tmpManualRewards;
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfManualRewards)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfManualRewards);
            }
            _tmpManualRewards = __converters.toManualRewards(_tmp_5);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _result = new JobTypeConfig(_tmpId,_tmpSheetsId,_tmpName,_tmpIsActive,_tmpIsShiftJob,_tmpIsOrionJob,_tmpRequiresShiftTime,_tmpBenefitSystemType,_tmpManualRewards,_tmpDescription,_tmpLastModified);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<JobTypeConfig>> getShiftJobTypes() {
    final String _sql = "SELECT * FROM job_type_configs WHERE isShiftJob = 1 AND isActive = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"job_type_configs"}, new Callable<List<JobTypeConfig>>() {
      @Override
      @NonNull
      public List<JobTypeConfig> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfIsShiftJob = CursorUtil.getColumnIndexOrThrow(_cursor, "isShiftJob");
          final int _cursorIndexOfIsOrionJob = CursorUtil.getColumnIndexOrThrow(_cursor, "isOrionJob");
          final int _cursorIndexOfRequiresShiftTime = CursorUtil.getColumnIndexOrThrow(_cursor, "requiresShiftTime");
          final int _cursorIndexOfBenefitSystemType = CursorUtil.getColumnIndexOrThrow(_cursor, "benefitSystemType");
          final int _cursorIndexOfManualRewards = CursorUtil.getColumnIndexOrThrow(_cursor, "manualRewards");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<JobTypeConfig> _result = new ArrayList<JobTypeConfig>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final JobTypeConfig _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSheetsId;
            if (_cursor.isNull(_cursorIndexOfSheetsId)) {
              _tmpSheetsId = null;
            } else {
              _tmpSheetsId = _cursor.getString(_cursorIndexOfSheetsId);
            }
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final boolean _tmpIsShiftJob;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsShiftJob);
            _tmpIsShiftJob = _tmp_1 != 0;
            final boolean _tmpIsOrionJob;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsOrionJob);
            _tmpIsOrionJob = _tmp_2 != 0;
            final boolean _tmpRequiresShiftTime;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfRequiresShiftTime);
            _tmpRequiresShiftTime = _tmp_3 != 0;
            final BenefitSystemType _tmpBenefitSystemType;
            final String _tmp_4;
            _tmp_4 = _cursor.getString(_cursorIndexOfBenefitSystemType);
            _tmpBenefitSystemType = __converters.toBenefitSystemType(_tmp_4);
            final ManualRewards _tmpManualRewards;
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfManualRewards)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfManualRewards);
            }
            _tmpManualRewards = __converters.toManualRewards(_tmp_5);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new JobTypeConfig(_tmpId,_tmpSheetsId,_tmpName,_tmpIsActive,_tmpIsShiftJob,_tmpIsOrionJob,_tmpRequiresShiftTime,_tmpBenefitSystemType,_tmpManualRewards,_tmpDescription,_tmpLastModified);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<JobTypeConfig>> getOrionJobTypes() {
    final String _sql = "SELECT * FROM job_type_configs WHERE isOrionJob = 1 AND isActive = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"job_type_configs"}, new Callable<List<JobTypeConfig>>() {
      @Override
      @NonNull
      public List<JobTypeConfig> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfIsShiftJob = CursorUtil.getColumnIndexOrThrow(_cursor, "isShiftJob");
          final int _cursorIndexOfIsOrionJob = CursorUtil.getColumnIndexOrThrow(_cursor, "isOrionJob");
          final int _cursorIndexOfRequiresShiftTime = CursorUtil.getColumnIndexOrThrow(_cursor, "requiresShiftTime");
          final int _cursorIndexOfBenefitSystemType = CursorUtil.getColumnIndexOrThrow(_cursor, "benefitSystemType");
          final int _cursorIndexOfManualRewards = CursorUtil.getColumnIndexOrThrow(_cursor, "manualRewards");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<JobTypeConfig> _result = new ArrayList<JobTypeConfig>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final JobTypeConfig _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSheetsId;
            if (_cursor.isNull(_cursorIndexOfSheetsId)) {
              _tmpSheetsId = null;
            } else {
              _tmpSheetsId = _cursor.getString(_cursorIndexOfSheetsId);
            }
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final boolean _tmpIsShiftJob;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsShiftJob);
            _tmpIsShiftJob = _tmp_1 != 0;
            final boolean _tmpIsOrionJob;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsOrionJob);
            _tmpIsOrionJob = _tmp_2 != 0;
            final boolean _tmpRequiresShiftTime;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfRequiresShiftTime);
            _tmpRequiresShiftTime = _tmp_3 != 0;
            final BenefitSystemType _tmpBenefitSystemType;
            final String _tmp_4;
            _tmp_4 = _cursor.getString(_cursorIndexOfBenefitSystemType);
            _tmpBenefitSystemType = __converters.toBenefitSystemType(_tmp_4);
            final ManualRewards _tmpManualRewards;
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfManualRewards)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfManualRewards);
            }
            _tmpManualRewards = __converters.toManualRewards(_tmp_5);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new JobTypeConfig(_tmpId,_tmpSheetsId,_tmpName,_tmpIsActive,_tmpIsShiftJob,_tmpIsOrionJob,_tmpRequiresShiftTime,_tmpBenefitSystemType,_tmpManualRewards,_tmpDescription,_tmpLastModified);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
