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
import com.eventmanager.app.data.models.Gender;
import com.eventmanager.app.data.models.Volunteer;
import com.eventmanager.app.data.models.VolunteerRank;
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
public final class VolunteerDao_Impl implements VolunteerDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Volunteer> __insertionAdapterOfVolunteer;

  private final Converters __converters = new Converters();

  private final EntityDeletionOrUpdateAdapter<Volunteer> __deletionAdapterOfVolunteer;

  private final EntityDeletionOrUpdateAdapter<Volunteer> __updateAdapterOfVolunteer;

  private final SharedSQLiteStatement __preparedStmtOfDeleteVolunteerById;

  private final SharedSQLiteStatement __preparedStmtOfUpdateLastModified;

  private final SharedSQLiteStatement __preparedStmtOfUpdateVolunteerStatus;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllVolunteers;

  public VolunteerDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfVolunteer = new EntityInsertionAdapter<Volunteer>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `volunteers` (`id`,`sheetsId`,`name`,`lastNameAbbreviation`,`email`,`phoneNumber`,`dateOfBirth`,`gender`,`currentRank`,`isActive`,`lastShiftDate`,`lastModified`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Volunteer entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getSheetsId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getSheetsId());
        }
        statement.bindString(3, entity.getName());
        statement.bindString(4, entity.getLastNameAbbreviation());
        statement.bindString(5, entity.getEmail());
        statement.bindString(6, entity.getPhoneNumber());
        statement.bindString(7, entity.getDateOfBirth());
        final String _tmp = __converters.fromGender(entity.getGender());
        if (_tmp == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, _tmp);
        }
        final String _tmp_1 = __converters.fromVolunteerRank(entity.getCurrentRank());
        if (_tmp_1 == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, _tmp_1);
        }
        final int _tmp_2 = entity.isActive() ? 1 : 0;
        statement.bindLong(10, _tmp_2);
        if (entity.getLastShiftDate() == null) {
          statement.bindNull(11);
        } else {
          statement.bindLong(11, entity.getLastShiftDate());
        }
        statement.bindLong(12, entity.getLastModified());
      }
    };
    this.__deletionAdapterOfVolunteer = new EntityDeletionOrUpdateAdapter<Volunteer>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `volunteers` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Volunteer entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfVolunteer = new EntityDeletionOrUpdateAdapter<Volunteer>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `volunteers` SET `id` = ?,`sheetsId` = ?,`name` = ?,`lastNameAbbreviation` = ?,`email` = ?,`phoneNumber` = ?,`dateOfBirth` = ?,`gender` = ?,`currentRank` = ?,`isActive` = ?,`lastShiftDate` = ?,`lastModified` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Volunteer entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getSheetsId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getSheetsId());
        }
        statement.bindString(3, entity.getName());
        statement.bindString(4, entity.getLastNameAbbreviation());
        statement.bindString(5, entity.getEmail());
        statement.bindString(6, entity.getPhoneNumber());
        statement.bindString(7, entity.getDateOfBirth());
        final String _tmp = __converters.fromGender(entity.getGender());
        if (_tmp == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, _tmp);
        }
        final String _tmp_1 = __converters.fromVolunteerRank(entity.getCurrentRank());
        if (_tmp_1 == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, _tmp_1);
        }
        final int _tmp_2 = entity.isActive() ? 1 : 0;
        statement.bindLong(10, _tmp_2);
        if (entity.getLastShiftDate() == null) {
          statement.bindNull(11);
        } else {
          statement.bindLong(11, entity.getLastShiftDate());
        }
        statement.bindLong(12, entity.getLastModified());
        statement.bindLong(13, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteVolunteerById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM volunteers WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateLastModified = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE volunteers SET lastModified = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateVolunteerStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE volunteers SET isActive = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllVolunteers = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM volunteers";
        return _query;
      }
    };
  }

  @Override
  public Object insertVolunteer(final Volunteer volunteer,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfVolunteer.insertAndReturnId(volunteer);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteVolunteer(final Volunteer volunteer,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfVolunteer.handle(volunteer);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateVolunteer(final Volunteer volunteer,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfVolunteer.handle(volunteer);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteVolunteerById(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteVolunteerById.acquire();
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
          __preparedStmtOfDeleteVolunteerById.release(_stmt);
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
  public Object updateVolunteerStatus(final long id, final boolean isActive,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateVolunteerStatus.acquire();
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
          __preparedStmtOfUpdateVolunteerStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllVolunteers(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllVolunteers.acquire();
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
          __preparedStmtOfDeleteAllVolunteers.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Volunteer>> getAllActiveVolunteers() {
    final String _sql = "SELECT * FROM volunteers WHERE isActive = 1 ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"volunteers"}, new Callable<List<Volunteer>>() {
      @Override
      @NonNull
      public List<Volunteer> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLastNameAbbreviation = CursorUtil.getColumnIndexOrThrow(_cursor, "lastNameAbbreviation");
          final int _cursorIndexOfEmail = CursorUtil.getColumnIndexOrThrow(_cursor, "email");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfDateOfBirth = CursorUtil.getColumnIndexOrThrow(_cursor, "dateOfBirth");
          final int _cursorIndexOfGender = CursorUtil.getColumnIndexOrThrow(_cursor, "gender");
          final int _cursorIndexOfCurrentRank = CursorUtil.getColumnIndexOrThrow(_cursor, "currentRank");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfLastShiftDate = CursorUtil.getColumnIndexOrThrow(_cursor, "lastShiftDate");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<Volunteer> _result = new ArrayList<Volunteer>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Volunteer _item;
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
            final String _tmpLastNameAbbreviation;
            _tmpLastNameAbbreviation = _cursor.getString(_cursorIndexOfLastNameAbbreviation);
            final String _tmpEmail;
            _tmpEmail = _cursor.getString(_cursorIndexOfEmail);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpDateOfBirth;
            _tmpDateOfBirth = _cursor.getString(_cursorIndexOfDateOfBirth);
            final Gender _tmpGender;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfGender)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfGender);
            }
            _tmpGender = __converters.toGender(_tmp);
            final VolunteerRank _tmpCurrentRank;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCurrentRank)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfCurrentRank);
            }
            _tmpCurrentRank = __converters.toVolunteerRank(_tmp_1);
            final boolean _tmpIsActive;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_2 != 0;
            final Long _tmpLastShiftDate;
            if (_cursor.isNull(_cursorIndexOfLastShiftDate)) {
              _tmpLastShiftDate = null;
            } else {
              _tmpLastShiftDate = _cursor.getLong(_cursorIndexOfLastShiftDate);
            }
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new Volunteer(_tmpId,_tmpSheetsId,_tmpName,_tmpLastNameAbbreviation,_tmpEmail,_tmpPhoneNumber,_tmpDateOfBirth,_tmpGender,_tmpCurrentRank,_tmpIsActive,_tmpLastShiftDate,_tmpLastModified);
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
  public Flow<List<Volunteer>> getAllVolunteers() {
    final String _sql = "SELECT * FROM volunteers ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"volunteers"}, new Callable<List<Volunteer>>() {
      @Override
      @NonNull
      public List<Volunteer> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLastNameAbbreviation = CursorUtil.getColumnIndexOrThrow(_cursor, "lastNameAbbreviation");
          final int _cursorIndexOfEmail = CursorUtil.getColumnIndexOrThrow(_cursor, "email");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfDateOfBirth = CursorUtil.getColumnIndexOrThrow(_cursor, "dateOfBirth");
          final int _cursorIndexOfGender = CursorUtil.getColumnIndexOrThrow(_cursor, "gender");
          final int _cursorIndexOfCurrentRank = CursorUtil.getColumnIndexOrThrow(_cursor, "currentRank");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfLastShiftDate = CursorUtil.getColumnIndexOrThrow(_cursor, "lastShiftDate");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<Volunteer> _result = new ArrayList<Volunteer>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Volunteer _item;
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
            final String _tmpLastNameAbbreviation;
            _tmpLastNameAbbreviation = _cursor.getString(_cursorIndexOfLastNameAbbreviation);
            final String _tmpEmail;
            _tmpEmail = _cursor.getString(_cursorIndexOfEmail);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpDateOfBirth;
            _tmpDateOfBirth = _cursor.getString(_cursorIndexOfDateOfBirth);
            final Gender _tmpGender;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfGender)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfGender);
            }
            _tmpGender = __converters.toGender(_tmp);
            final VolunteerRank _tmpCurrentRank;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCurrentRank)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfCurrentRank);
            }
            _tmpCurrentRank = __converters.toVolunteerRank(_tmp_1);
            final boolean _tmpIsActive;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_2 != 0;
            final Long _tmpLastShiftDate;
            if (_cursor.isNull(_cursorIndexOfLastShiftDate)) {
              _tmpLastShiftDate = null;
            } else {
              _tmpLastShiftDate = _cursor.getLong(_cursorIndexOfLastShiftDate);
            }
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new Volunteer(_tmpId,_tmpSheetsId,_tmpName,_tmpLastNameAbbreviation,_tmpEmail,_tmpPhoneNumber,_tmpDateOfBirth,_tmpGender,_tmpCurrentRank,_tmpIsActive,_tmpLastShiftDate,_tmpLastModified);
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
  public Flow<List<Volunteer>> getInactiveVolunteers() {
    final String _sql = "SELECT * FROM volunteers WHERE isActive = 0 ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"volunteers"}, new Callable<List<Volunteer>>() {
      @Override
      @NonNull
      public List<Volunteer> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLastNameAbbreviation = CursorUtil.getColumnIndexOrThrow(_cursor, "lastNameAbbreviation");
          final int _cursorIndexOfEmail = CursorUtil.getColumnIndexOrThrow(_cursor, "email");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfDateOfBirth = CursorUtil.getColumnIndexOrThrow(_cursor, "dateOfBirth");
          final int _cursorIndexOfGender = CursorUtil.getColumnIndexOrThrow(_cursor, "gender");
          final int _cursorIndexOfCurrentRank = CursorUtil.getColumnIndexOrThrow(_cursor, "currentRank");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfLastShiftDate = CursorUtil.getColumnIndexOrThrow(_cursor, "lastShiftDate");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<Volunteer> _result = new ArrayList<Volunteer>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Volunteer _item;
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
            final String _tmpLastNameAbbreviation;
            _tmpLastNameAbbreviation = _cursor.getString(_cursorIndexOfLastNameAbbreviation);
            final String _tmpEmail;
            _tmpEmail = _cursor.getString(_cursorIndexOfEmail);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpDateOfBirth;
            _tmpDateOfBirth = _cursor.getString(_cursorIndexOfDateOfBirth);
            final Gender _tmpGender;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfGender)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfGender);
            }
            _tmpGender = __converters.toGender(_tmp);
            final VolunteerRank _tmpCurrentRank;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCurrentRank)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfCurrentRank);
            }
            _tmpCurrentRank = __converters.toVolunteerRank(_tmp_1);
            final boolean _tmpIsActive;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_2 != 0;
            final Long _tmpLastShiftDate;
            if (_cursor.isNull(_cursorIndexOfLastShiftDate)) {
              _tmpLastShiftDate = null;
            } else {
              _tmpLastShiftDate = _cursor.getLong(_cursorIndexOfLastShiftDate);
            }
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new Volunteer(_tmpId,_tmpSheetsId,_tmpName,_tmpLastNameAbbreviation,_tmpEmail,_tmpPhoneNumber,_tmpDateOfBirth,_tmpGender,_tmpCurrentRank,_tmpIsActive,_tmpLastShiftDate,_tmpLastModified);
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
  public Object getVolunteerById(final long id, final Continuation<? super Volunteer> $completion) {
    final String _sql = "SELECT * FROM volunteers WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Volunteer>() {
      @Override
      @Nullable
      public Volunteer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLastNameAbbreviation = CursorUtil.getColumnIndexOrThrow(_cursor, "lastNameAbbreviation");
          final int _cursorIndexOfEmail = CursorUtil.getColumnIndexOrThrow(_cursor, "email");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfDateOfBirth = CursorUtil.getColumnIndexOrThrow(_cursor, "dateOfBirth");
          final int _cursorIndexOfGender = CursorUtil.getColumnIndexOrThrow(_cursor, "gender");
          final int _cursorIndexOfCurrentRank = CursorUtil.getColumnIndexOrThrow(_cursor, "currentRank");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfLastShiftDate = CursorUtil.getColumnIndexOrThrow(_cursor, "lastShiftDate");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final Volunteer _result;
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
            final String _tmpLastNameAbbreviation;
            _tmpLastNameAbbreviation = _cursor.getString(_cursorIndexOfLastNameAbbreviation);
            final String _tmpEmail;
            _tmpEmail = _cursor.getString(_cursorIndexOfEmail);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpDateOfBirth;
            _tmpDateOfBirth = _cursor.getString(_cursorIndexOfDateOfBirth);
            final Gender _tmpGender;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfGender)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfGender);
            }
            _tmpGender = __converters.toGender(_tmp);
            final VolunteerRank _tmpCurrentRank;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCurrentRank)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfCurrentRank);
            }
            _tmpCurrentRank = __converters.toVolunteerRank(_tmp_1);
            final boolean _tmpIsActive;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_2 != 0;
            final Long _tmpLastShiftDate;
            if (_cursor.isNull(_cursorIndexOfLastShiftDate)) {
              _tmpLastShiftDate = null;
            } else {
              _tmpLastShiftDate = _cursor.getLong(_cursorIndexOfLastShiftDate);
            }
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _result = new Volunteer(_tmpId,_tmpSheetsId,_tmpName,_tmpLastNameAbbreviation,_tmpEmail,_tmpPhoneNumber,_tmpDateOfBirth,_tmpGender,_tmpCurrentRank,_tmpIsActive,_tmpLastShiftDate,_tmpLastModified);
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
  public Flow<List<Volunteer>> getVolunteersByRank(final VolunteerRank rank) {
    final String _sql = "SELECT * FROM volunteers WHERE currentRank = ? AND isActive = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final String _tmp = __converters.fromVolunteerRank(rank);
    if (_tmp == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, _tmp);
    }
    return CoroutinesRoom.createFlow(__db, false, new String[] {"volunteers"}, new Callable<List<Volunteer>>() {
      @Override
      @NonNull
      public List<Volunteer> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLastNameAbbreviation = CursorUtil.getColumnIndexOrThrow(_cursor, "lastNameAbbreviation");
          final int _cursorIndexOfEmail = CursorUtil.getColumnIndexOrThrow(_cursor, "email");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfDateOfBirth = CursorUtil.getColumnIndexOrThrow(_cursor, "dateOfBirth");
          final int _cursorIndexOfGender = CursorUtil.getColumnIndexOrThrow(_cursor, "gender");
          final int _cursorIndexOfCurrentRank = CursorUtil.getColumnIndexOrThrow(_cursor, "currentRank");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfLastShiftDate = CursorUtil.getColumnIndexOrThrow(_cursor, "lastShiftDate");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<Volunteer> _result = new ArrayList<Volunteer>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Volunteer _item;
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
            final String _tmpLastNameAbbreviation;
            _tmpLastNameAbbreviation = _cursor.getString(_cursorIndexOfLastNameAbbreviation);
            final String _tmpEmail;
            _tmpEmail = _cursor.getString(_cursorIndexOfEmail);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpDateOfBirth;
            _tmpDateOfBirth = _cursor.getString(_cursorIndexOfDateOfBirth);
            final Gender _tmpGender;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfGender)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfGender);
            }
            _tmpGender = __converters.toGender(_tmp_1);
            final VolunteerRank _tmpCurrentRank;
            final String _tmp_2;
            if (_cursor.isNull(_cursorIndexOfCurrentRank)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getString(_cursorIndexOfCurrentRank);
            }
            _tmpCurrentRank = __converters.toVolunteerRank(_tmp_2);
            final boolean _tmpIsActive;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_3 != 0;
            final Long _tmpLastShiftDate;
            if (_cursor.isNull(_cursorIndexOfLastShiftDate)) {
              _tmpLastShiftDate = null;
            } else {
              _tmpLastShiftDate = _cursor.getLong(_cursorIndexOfLastShiftDate);
            }
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new Volunteer(_tmpId,_tmpSheetsId,_tmpName,_tmpLastNameAbbreviation,_tmpEmail,_tmpPhoneNumber,_tmpDateOfBirth,_tmpGender,_tmpCurrentRank,_tmpIsActive,_tmpLastShiftDate,_tmpLastModified);
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
  public Object getVolunteersModifiedAfter(final long timestamp,
      final Continuation<? super List<Volunteer>> $completion) {
    final String _sql = "SELECT * FROM volunteers WHERE lastModified > ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, timestamp);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Volunteer>>() {
      @Override
      @NonNull
      public List<Volunteer> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLastNameAbbreviation = CursorUtil.getColumnIndexOrThrow(_cursor, "lastNameAbbreviation");
          final int _cursorIndexOfEmail = CursorUtil.getColumnIndexOrThrow(_cursor, "email");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfDateOfBirth = CursorUtil.getColumnIndexOrThrow(_cursor, "dateOfBirth");
          final int _cursorIndexOfGender = CursorUtil.getColumnIndexOrThrow(_cursor, "gender");
          final int _cursorIndexOfCurrentRank = CursorUtil.getColumnIndexOrThrow(_cursor, "currentRank");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfLastShiftDate = CursorUtil.getColumnIndexOrThrow(_cursor, "lastShiftDate");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<Volunteer> _result = new ArrayList<Volunteer>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Volunteer _item;
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
            final String _tmpLastNameAbbreviation;
            _tmpLastNameAbbreviation = _cursor.getString(_cursorIndexOfLastNameAbbreviation);
            final String _tmpEmail;
            _tmpEmail = _cursor.getString(_cursorIndexOfEmail);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpDateOfBirth;
            _tmpDateOfBirth = _cursor.getString(_cursorIndexOfDateOfBirth);
            final Gender _tmpGender;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfGender)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfGender);
            }
            _tmpGender = __converters.toGender(_tmp);
            final VolunteerRank _tmpCurrentRank;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCurrentRank)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfCurrentRank);
            }
            _tmpCurrentRank = __converters.toVolunteerRank(_tmp_1);
            final boolean _tmpIsActive;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_2 != 0;
            final Long _tmpLastShiftDate;
            if (_cursor.isNull(_cursorIndexOfLastShiftDate)) {
              _tmpLastShiftDate = null;
            } else {
              _tmpLastShiftDate = _cursor.getLong(_cursorIndexOfLastShiftDate);
            }
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new Volunteer(_tmpId,_tmpSheetsId,_tmpName,_tmpLastNameAbbreviation,_tmpEmail,_tmpPhoneNumber,_tmpDateOfBirth,_tmpGender,_tmpCurrentRank,_tmpIsActive,_tmpLastShiftDate,_tmpLastModified);
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
  public Object getVolunteerBySheetsId(final String sheetsId,
      final Continuation<? super Volunteer> $completion) {
    final String _sql = "SELECT * FROM volunteers WHERE sheetsId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sheetsId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Volunteer>() {
      @Override
      @Nullable
      public Volunteer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLastNameAbbreviation = CursorUtil.getColumnIndexOrThrow(_cursor, "lastNameAbbreviation");
          final int _cursorIndexOfEmail = CursorUtil.getColumnIndexOrThrow(_cursor, "email");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfDateOfBirth = CursorUtil.getColumnIndexOrThrow(_cursor, "dateOfBirth");
          final int _cursorIndexOfGender = CursorUtil.getColumnIndexOrThrow(_cursor, "gender");
          final int _cursorIndexOfCurrentRank = CursorUtil.getColumnIndexOrThrow(_cursor, "currentRank");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfLastShiftDate = CursorUtil.getColumnIndexOrThrow(_cursor, "lastShiftDate");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final Volunteer _result;
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
            final String _tmpLastNameAbbreviation;
            _tmpLastNameAbbreviation = _cursor.getString(_cursorIndexOfLastNameAbbreviation);
            final String _tmpEmail;
            _tmpEmail = _cursor.getString(_cursorIndexOfEmail);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpDateOfBirth;
            _tmpDateOfBirth = _cursor.getString(_cursorIndexOfDateOfBirth);
            final Gender _tmpGender;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfGender)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfGender);
            }
            _tmpGender = __converters.toGender(_tmp);
            final VolunteerRank _tmpCurrentRank;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCurrentRank)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfCurrentRank);
            }
            _tmpCurrentRank = __converters.toVolunteerRank(_tmp_1);
            final boolean _tmpIsActive;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_2 != 0;
            final Long _tmpLastShiftDate;
            if (_cursor.isNull(_cursorIndexOfLastShiftDate)) {
              _tmpLastShiftDate = null;
            } else {
              _tmpLastShiftDate = _cursor.getLong(_cursorIndexOfLastShiftDate);
            }
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _result = new Volunteer(_tmpId,_tmpSheetsId,_tmpName,_tmpLastNameAbbreviation,_tmpEmail,_tmpPhoneNumber,_tmpDateOfBirth,_tmpGender,_tmpCurrentRank,_tmpIsActive,_tmpLastShiftDate,_tmpLastModified);
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
  public Object getVolunteerByName(final String name,
      final Continuation<? super Volunteer> $completion) {
    final String _sql = "SELECT * FROM volunteers WHERE name = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, name);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Volunteer>() {
      @Override
      @Nullable
      public Volunteer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLastNameAbbreviation = CursorUtil.getColumnIndexOrThrow(_cursor, "lastNameAbbreviation");
          final int _cursorIndexOfEmail = CursorUtil.getColumnIndexOrThrow(_cursor, "email");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfDateOfBirth = CursorUtil.getColumnIndexOrThrow(_cursor, "dateOfBirth");
          final int _cursorIndexOfGender = CursorUtil.getColumnIndexOrThrow(_cursor, "gender");
          final int _cursorIndexOfCurrentRank = CursorUtil.getColumnIndexOrThrow(_cursor, "currentRank");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfLastShiftDate = CursorUtil.getColumnIndexOrThrow(_cursor, "lastShiftDate");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final Volunteer _result;
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
            final String _tmpLastNameAbbreviation;
            _tmpLastNameAbbreviation = _cursor.getString(_cursorIndexOfLastNameAbbreviation);
            final String _tmpEmail;
            _tmpEmail = _cursor.getString(_cursorIndexOfEmail);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpDateOfBirth;
            _tmpDateOfBirth = _cursor.getString(_cursorIndexOfDateOfBirth);
            final Gender _tmpGender;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfGender)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfGender);
            }
            _tmpGender = __converters.toGender(_tmp);
            final VolunteerRank _tmpCurrentRank;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCurrentRank)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfCurrentRank);
            }
            _tmpCurrentRank = __converters.toVolunteerRank(_tmp_1);
            final boolean _tmpIsActive;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_2 != 0;
            final Long _tmpLastShiftDate;
            if (_cursor.isNull(_cursorIndexOfLastShiftDate)) {
              _tmpLastShiftDate = null;
            } else {
              _tmpLastShiftDate = _cursor.getLong(_cursorIndexOfLastShiftDate);
            }
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _result = new Volunteer(_tmpId,_tmpSheetsId,_tmpName,_tmpLastNameAbbreviation,_tmpEmail,_tmpPhoneNumber,_tmpDateOfBirth,_tmpGender,_tmpCurrentRank,_tmpIsActive,_tmpLastShiftDate,_tmpLastModified);
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
