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
import com.eventmanager.app.data.models.Converters;
import com.eventmanager.app.data.models.Job;
import com.eventmanager.app.data.models.JobType;
import com.eventmanager.app.data.models.ShiftTime;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
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
public final class JobDao_Impl implements JobDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Job> __insertionAdapterOfJob;

  private final Converters __converters = new Converters();

  private final EntityDeletionOrUpdateAdapter<Job> __deletionAdapterOfJob;

  private final EntityDeletionOrUpdateAdapter<Job> __updateAdapterOfJob;

  private final SharedSQLiteStatement __preparedStmtOfDeleteJobById;

  private final SharedSQLiteStatement __preparedStmtOfUpdateLastModified;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllJobs;

  public JobDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfJob = new EntityInsertionAdapter<Job>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `jobs` (`id`,`sheetsId`,`volunteerId`,`jobType`,`jobTypeName`,`venueName`,`date`,`shiftTime`,`notes`,`lastModified`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Job entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getSheetsId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getSheetsId());
        }
        statement.bindLong(3, entity.getVolunteerId());
        final String _tmp = __converters.fromJobType(entity.getJobType());
        statement.bindString(4, _tmp);
        statement.bindString(5, entity.getJobTypeName());
        statement.bindString(6, entity.getVenueName());
        statement.bindLong(7, entity.getDate());
        final String _tmp_1 = __converters.fromShiftTime(entity.getShiftTime());
        statement.bindString(8, _tmp_1);
        statement.bindString(9, entity.getNotes());
        statement.bindLong(10, entity.getLastModified());
      }
    };
    this.__deletionAdapterOfJob = new EntityDeletionOrUpdateAdapter<Job>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `jobs` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Job entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfJob = new EntityDeletionOrUpdateAdapter<Job>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `jobs` SET `id` = ?,`sheetsId` = ?,`volunteerId` = ?,`jobType` = ?,`jobTypeName` = ?,`venueName` = ?,`date` = ?,`shiftTime` = ?,`notes` = ?,`lastModified` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Job entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getSheetsId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getSheetsId());
        }
        statement.bindLong(3, entity.getVolunteerId());
        final String _tmp = __converters.fromJobType(entity.getJobType());
        statement.bindString(4, _tmp);
        statement.bindString(5, entity.getJobTypeName());
        statement.bindString(6, entity.getVenueName());
        statement.bindLong(7, entity.getDate());
        final String _tmp_1 = __converters.fromShiftTime(entity.getShiftTime());
        statement.bindString(8, _tmp_1);
        statement.bindString(9, entity.getNotes());
        statement.bindLong(10, entity.getLastModified());
        statement.bindLong(11, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteJobById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM jobs WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateLastModified = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE jobs SET lastModified = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllJobs = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM jobs";
        return _query;
      }
    };
  }

  @Override
  public Object insertJob(final Job job, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfJob.insertAndReturnId(job);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteJob(final Job job, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfJob.handle(job);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateJob(final Job job, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfJob.handle(job);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteJobById(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteJobById.acquire();
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
          __preparedStmtOfDeleteJobById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateLastModified(final long id, final long timestamp,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateLastModified.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, timestamp);
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
          __preparedStmtOfUpdateLastModified.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllJobs(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllJobs.acquire();
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
          __preparedStmtOfDeleteAllJobs.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Job>> getAllJobs() {
    final String _sql = "SELECT * FROM jobs ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"jobs"}, new Callable<List<Job>>() {
      @Override
      @NonNull
      public List<Job> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfVolunteerId = CursorUtil.getColumnIndexOrThrow(_cursor, "volunteerId");
          final int _cursorIndexOfJobType = CursorUtil.getColumnIndexOrThrow(_cursor, "jobType");
          final int _cursorIndexOfJobTypeName = CursorUtil.getColumnIndexOrThrow(_cursor, "jobTypeName");
          final int _cursorIndexOfVenueName = CursorUtil.getColumnIndexOrThrow(_cursor, "venueName");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfShiftTime = CursorUtil.getColumnIndexOrThrow(_cursor, "shiftTime");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<Job> _result = new ArrayList<Job>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Job _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSheetsId;
            if (_cursor.isNull(_cursorIndexOfSheetsId)) {
              _tmpSheetsId = null;
            } else {
              _tmpSheetsId = _cursor.getString(_cursorIndexOfSheetsId);
            }
            final long _tmpVolunteerId;
            _tmpVolunteerId = _cursor.getLong(_cursorIndexOfVolunteerId);
            final JobType _tmpJobType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfJobType);
            _tmpJobType = __converters.toJobType(_tmp);
            final String _tmpJobTypeName;
            _tmpJobTypeName = _cursor.getString(_cursorIndexOfJobTypeName);
            final String _tmpVenueName;
            _tmpVenueName = _cursor.getString(_cursorIndexOfVenueName);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final ShiftTime _tmpShiftTime;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfShiftTime);
            _tmpShiftTime = __converters.toShiftTime(_tmp_1);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new Job(_tmpId,_tmpSheetsId,_tmpVolunteerId,_tmpJobType,_tmpJobTypeName,_tmpVenueName,_tmpDate,_tmpShiftTime,_tmpNotes,_tmpLastModified);
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
  public Flow<List<Job>> getJobsByVolunteer(final long volunteerId) {
    final String _sql = "SELECT * FROM jobs WHERE volunteerId = ? ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, volunteerId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"jobs"}, new Callable<List<Job>>() {
      @Override
      @NonNull
      public List<Job> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfVolunteerId = CursorUtil.getColumnIndexOrThrow(_cursor, "volunteerId");
          final int _cursorIndexOfJobType = CursorUtil.getColumnIndexOrThrow(_cursor, "jobType");
          final int _cursorIndexOfJobTypeName = CursorUtil.getColumnIndexOrThrow(_cursor, "jobTypeName");
          final int _cursorIndexOfVenueName = CursorUtil.getColumnIndexOrThrow(_cursor, "venueName");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfShiftTime = CursorUtil.getColumnIndexOrThrow(_cursor, "shiftTime");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<Job> _result = new ArrayList<Job>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Job _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSheetsId;
            if (_cursor.isNull(_cursorIndexOfSheetsId)) {
              _tmpSheetsId = null;
            } else {
              _tmpSheetsId = _cursor.getString(_cursorIndexOfSheetsId);
            }
            final long _tmpVolunteerId;
            _tmpVolunteerId = _cursor.getLong(_cursorIndexOfVolunteerId);
            final JobType _tmpJobType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfJobType);
            _tmpJobType = __converters.toJobType(_tmp);
            final String _tmpJobTypeName;
            _tmpJobTypeName = _cursor.getString(_cursorIndexOfJobTypeName);
            final String _tmpVenueName;
            _tmpVenueName = _cursor.getString(_cursorIndexOfVenueName);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final ShiftTime _tmpShiftTime;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfShiftTime);
            _tmpShiftTime = __converters.toShiftTime(_tmp_1);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new Job(_tmpId,_tmpSheetsId,_tmpVolunteerId,_tmpJobType,_tmpJobTypeName,_tmpVenueName,_tmpDate,_tmpShiftTime,_tmpNotes,_tmpLastModified);
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
  public Flow<List<Job>> getJobsByVenue(final String venueName) {
    final String _sql = "SELECT * FROM jobs WHERE venueName = ? ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, venueName);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"jobs"}, new Callable<List<Job>>() {
      @Override
      @NonNull
      public List<Job> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfVolunteerId = CursorUtil.getColumnIndexOrThrow(_cursor, "volunteerId");
          final int _cursorIndexOfJobType = CursorUtil.getColumnIndexOrThrow(_cursor, "jobType");
          final int _cursorIndexOfJobTypeName = CursorUtil.getColumnIndexOrThrow(_cursor, "jobTypeName");
          final int _cursorIndexOfVenueName = CursorUtil.getColumnIndexOrThrow(_cursor, "venueName");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfShiftTime = CursorUtil.getColumnIndexOrThrow(_cursor, "shiftTime");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<Job> _result = new ArrayList<Job>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Job _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSheetsId;
            if (_cursor.isNull(_cursorIndexOfSheetsId)) {
              _tmpSheetsId = null;
            } else {
              _tmpSheetsId = _cursor.getString(_cursorIndexOfSheetsId);
            }
            final long _tmpVolunteerId;
            _tmpVolunteerId = _cursor.getLong(_cursorIndexOfVolunteerId);
            final JobType _tmpJobType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfJobType);
            _tmpJobType = __converters.toJobType(_tmp);
            final String _tmpJobTypeName;
            _tmpJobTypeName = _cursor.getString(_cursorIndexOfJobTypeName);
            final String _tmpVenueName;
            _tmpVenueName = _cursor.getString(_cursorIndexOfVenueName);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final ShiftTime _tmpShiftTime;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfShiftTime);
            _tmpShiftTime = __converters.toShiftTime(_tmp_1);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new Job(_tmpId,_tmpSheetsId,_tmpVolunteerId,_tmpJobType,_tmpJobTypeName,_tmpVenueName,_tmpDate,_tmpShiftTime,_tmpNotes,_tmpLastModified);
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
  public Object getJobById(final long id, final Continuation<? super Job> $completion) {
    final String _sql = "SELECT * FROM jobs WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Job>() {
      @Override
      @Nullable
      public Job call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfVolunteerId = CursorUtil.getColumnIndexOrThrow(_cursor, "volunteerId");
          final int _cursorIndexOfJobType = CursorUtil.getColumnIndexOrThrow(_cursor, "jobType");
          final int _cursorIndexOfJobTypeName = CursorUtil.getColumnIndexOrThrow(_cursor, "jobTypeName");
          final int _cursorIndexOfVenueName = CursorUtil.getColumnIndexOrThrow(_cursor, "venueName");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfShiftTime = CursorUtil.getColumnIndexOrThrow(_cursor, "shiftTime");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final Job _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSheetsId;
            if (_cursor.isNull(_cursorIndexOfSheetsId)) {
              _tmpSheetsId = null;
            } else {
              _tmpSheetsId = _cursor.getString(_cursorIndexOfSheetsId);
            }
            final long _tmpVolunteerId;
            _tmpVolunteerId = _cursor.getLong(_cursorIndexOfVolunteerId);
            final JobType _tmpJobType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfJobType);
            _tmpJobType = __converters.toJobType(_tmp);
            final String _tmpJobTypeName;
            _tmpJobTypeName = _cursor.getString(_cursorIndexOfJobTypeName);
            final String _tmpVenueName;
            _tmpVenueName = _cursor.getString(_cursorIndexOfVenueName);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final ShiftTime _tmpShiftTime;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfShiftTime);
            _tmpShiftTime = __converters.toShiftTime(_tmp_1);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _result = new Job(_tmpId,_tmpSheetsId,_tmpVolunteerId,_tmpJobType,_tmpJobTypeName,_tmpVenueName,_tmpDate,_tmpShiftTime,_tmpNotes,_tmpLastModified);
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
  public Flow<List<Job>> getJobsByDateRange(final long startDate, final long endDate) {
    final String _sql = "SELECT * FROM jobs WHERE date BETWEEN ? AND ? ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startDate);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endDate);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"jobs"}, new Callable<List<Job>>() {
      @Override
      @NonNull
      public List<Job> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfVolunteerId = CursorUtil.getColumnIndexOrThrow(_cursor, "volunteerId");
          final int _cursorIndexOfJobType = CursorUtil.getColumnIndexOrThrow(_cursor, "jobType");
          final int _cursorIndexOfJobTypeName = CursorUtil.getColumnIndexOrThrow(_cursor, "jobTypeName");
          final int _cursorIndexOfVenueName = CursorUtil.getColumnIndexOrThrow(_cursor, "venueName");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfShiftTime = CursorUtil.getColumnIndexOrThrow(_cursor, "shiftTime");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<Job> _result = new ArrayList<Job>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Job _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSheetsId;
            if (_cursor.isNull(_cursorIndexOfSheetsId)) {
              _tmpSheetsId = null;
            } else {
              _tmpSheetsId = _cursor.getString(_cursorIndexOfSheetsId);
            }
            final long _tmpVolunteerId;
            _tmpVolunteerId = _cursor.getLong(_cursorIndexOfVolunteerId);
            final JobType _tmpJobType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfJobType);
            _tmpJobType = __converters.toJobType(_tmp);
            final String _tmpJobTypeName;
            _tmpJobTypeName = _cursor.getString(_cursorIndexOfJobTypeName);
            final String _tmpVenueName;
            _tmpVenueName = _cursor.getString(_cursorIndexOfVenueName);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final ShiftTime _tmpShiftTime;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfShiftTime);
            _tmpShiftTime = __converters.toShiftTime(_tmp_1);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new Job(_tmpId,_tmpSheetsId,_tmpVolunteerId,_tmpJobType,_tmpJobTypeName,_tmpVenueName,_tmpDate,_tmpShiftTime,_tmpNotes,_tmpLastModified);
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
  public Object getJobsModifiedAfter(final long timestamp,
      final Continuation<? super List<Job>> $completion) {
    final String _sql = "SELECT * FROM jobs WHERE lastModified > ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, timestamp);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Job>>() {
      @Override
      @NonNull
      public List<Job> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfVolunteerId = CursorUtil.getColumnIndexOrThrow(_cursor, "volunteerId");
          final int _cursorIndexOfJobType = CursorUtil.getColumnIndexOrThrow(_cursor, "jobType");
          final int _cursorIndexOfJobTypeName = CursorUtil.getColumnIndexOrThrow(_cursor, "jobTypeName");
          final int _cursorIndexOfVenueName = CursorUtil.getColumnIndexOrThrow(_cursor, "venueName");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfShiftTime = CursorUtil.getColumnIndexOrThrow(_cursor, "shiftTime");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<Job> _result = new ArrayList<Job>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Job _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSheetsId;
            if (_cursor.isNull(_cursorIndexOfSheetsId)) {
              _tmpSheetsId = null;
            } else {
              _tmpSheetsId = _cursor.getString(_cursorIndexOfSheetsId);
            }
            final long _tmpVolunteerId;
            _tmpVolunteerId = _cursor.getLong(_cursorIndexOfVolunteerId);
            final JobType _tmpJobType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfJobType);
            _tmpJobType = __converters.toJobType(_tmp);
            final String _tmpJobTypeName;
            _tmpJobTypeName = _cursor.getString(_cursorIndexOfJobTypeName);
            final String _tmpVenueName;
            _tmpVenueName = _cursor.getString(_cursorIndexOfVenueName);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final ShiftTime _tmpShiftTime;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfShiftTime);
            _tmpShiftTime = __converters.toShiftTime(_tmp_1);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new Job(_tmpId,_tmpSheetsId,_tmpVolunteerId,_tmpJobType,_tmpJobTypeName,_tmpVenueName,_tmpDate,_tmpShiftTime,_tmpNotes,_tmpLastModified);
            _result.add(_item);
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
  public Object getJobCountForMonth(final long volunteerId, final long monthStart,
      final long monthEnd, final Continuation<? super Integer> $completion) {
    final String _sql = "\n"
            + "        SELECT COUNT(*) FROM jobs \n"
            + "        WHERE volunteerId = ? \n"
            + "        AND date >= ? \n"
            + "        AND date <= ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, volunteerId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, monthStart);
    _argIndex = 3;
    _statement.bindLong(_argIndex, monthEnd);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
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
  public Object getAfterMidnightJobCount(final long volunteerId, final long monthStart,
      final long monthEnd, final Continuation<? super Integer> $completion) {
    final String _sql = "\n"
            + "        SELECT COUNT(*) FROM jobs \n"
            + "        WHERE volunteerId = ? \n"
            + "        AND shiftTime = 'AFTER_MIDNIGHT'\n"
            + "        AND date >= ? \n"
            + "        AND date <= ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, volunteerId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, monthStart);
    _argIndex = 3;
    _statement.bindLong(_argIndex, monthEnd);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
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
  public Object getBeforeMidnightJobCount(final long volunteerId, final long monthStart,
      final long monthEnd, final Continuation<? super Integer> $completion) {
    final String _sql = "\n"
            + "        SELECT COUNT(*) FROM jobs \n"
            + "        WHERE volunteerId = ? \n"
            + "        AND shiftTime = 'BEFORE_MIDNIGHT'\n"
            + "        AND date >= ? \n"
            + "        AND date <= ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, volunteerId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, monthStart);
    _argIndex = 3;
    _statement.bindLong(_argIndex, monthEnd);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
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
  public Object getJobBySheetsId(final String sheetsId,
      final Continuation<? super Job> $completion) {
    final String _sql = "SELECT * FROM jobs WHERE sheetsId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sheetsId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Job>() {
      @Override
      @Nullable
      public Job call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfVolunteerId = CursorUtil.getColumnIndexOrThrow(_cursor, "volunteerId");
          final int _cursorIndexOfJobType = CursorUtil.getColumnIndexOrThrow(_cursor, "jobType");
          final int _cursorIndexOfJobTypeName = CursorUtil.getColumnIndexOrThrow(_cursor, "jobTypeName");
          final int _cursorIndexOfVenueName = CursorUtil.getColumnIndexOrThrow(_cursor, "venueName");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfShiftTime = CursorUtil.getColumnIndexOrThrow(_cursor, "shiftTime");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final Job _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSheetsId;
            if (_cursor.isNull(_cursorIndexOfSheetsId)) {
              _tmpSheetsId = null;
            } else {
              _tmpSheetsId = _cursor.getString(_cursorIndexOfSheetsId);
            }
            final long _tmpVolunteerId;
            _tmpVolunteerId = _cursor.getLong(_cursorIndexOfVolunteerId);
            final JobType _tmpJobType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfJobType);
            _tmpJobType = __converters.toJobType(_tmp);
            final String _tmpJobTypeName;
            _tmpJobTypeName = _cursor.getString(_cursorIndexOfJobTypeName);
            final String _tmpVenueName;
            _tmpVenueName = _cursor.getString(_cursorIndexOfVenueName);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final ShiftTime _tmpShiftTime;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfShiftTime);
            _tmpShiftTime = __converters.toShiftTime(_tmp_1);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _result = new Job(_tmpId,_tmpSheetsId,_tmpVolunteerId,_tmpJobType,_tmpJobTypeName,_tmpVenueName,_tmpDate,_tmpShiftTime,_tmpNotes,_tmpLastModified);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
