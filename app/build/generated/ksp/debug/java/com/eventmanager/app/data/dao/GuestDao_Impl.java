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
import com.eventmanager.app.data.models.Guest;
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
public final class GuestDao_Impl implements GuestDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Guest> __insertionAdapterOfGuest;

  private final EntityDeletionOrUpdateAdapter<Guest> __deletionAdapterOfGuest;

  private final EntityDeletionOrUpdateAdapter<Guest> __updateAdapterOfGuest;

  private final SharedSQLiteStatement __preparedStmtOfDeleteGuestById;

  private final SharedSQLiteStatement __preparedStmtOfUpdateLastModified;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllGuests;

  public GuestDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfGuest = new EntityInsertionAdapter<Guest>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `guests` (`id`,`sheetsId`,`name`,`lastNameAbbreviation`,`invitations`,`venueName`,`notes`,`isVolunteerBenefit`,`volunteerId`,`lastModified`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Guest entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getSheetsId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getSheetsId());
        }
        statement.bindString(3, entity.getName());
        statement.bindString(4, entity.getLastNameAbbreviation());
        statement.bindLong(5, entity.getInvitations());
        statement.bindString(6, entity.getVenueName());
        statement.bindString(7, entity.getNotes());
        final int _tmp = entity.isVolunteerBenefit() ? 1 : 0;
        statement.bindLong(8, _tmp);
        if (entity.getVolunteerId() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getVolunteerId());
        }
        statement.bindLong(10, entity.getLastModified());
      }
    };
    this.__deletionAdapterOfGuest = new EntityDeletionOrUpdateAdapter<Guest>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `guests` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Guest entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfGuest = new EntityDeletionOrUpdateAdapter<Guest>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `guests` SET `id` = ?,`sheetsId` = ?,`name` = ?,`lastNameAbbreviation` = ?,`invitations` = ?,`venueName` = ?,`notes` = ?,`isVolunteerBenefit` = ?,`volunteerId` = ?,`lastModified` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Guest entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getSheetsId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getSheetsId());
        }
        statement.bindString(3, entity.getName());
        statement.bindString(4, entity.getLastNameAbbreviation());
        statement.bindLong(5, entity.getInvitations());
        statement.bindString(6, entity.getVenueName());
        statement.bindString(7, entity.getNotes());
        final int _tmp = entity.isVolunteerBenefit() ? 1 : 0;
        statement.bindLong(8, _tmp);
        if (entity.getVolunteerId() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getVolunteerId());
        }
        statement.bindLong(10, entity.getLastModified());
        statement.bindLong(11, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteGuestById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM guests WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateLastModified = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE guests SET lastModified = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllGuests = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM guests";
        return _query;
      }
    };
  }

  @Override
  public Object insertGuest(final Guest guest, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfGuest.insertAndReturnId(guest);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteGuest(final Guest guest, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfGuest.handle(guest);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateGuest(final Guest guest, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfGuest.handle(guest);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteGuestById(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteGuestById.acquire();
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
          __preparedStmtOfDeleteGuestById.release(_stmt);
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
  public Object deleteAllGuests(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllGuests.acquire();
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
          __preparedStmtOfDeleteAllGuests.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Guest>> getGuestsByVenue(final String venueName) {
    final String _sql = "SELECT * FROM guests WHERE venueName = ? ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, venueName);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"guests"}, new Callable<List<Guest>>() {
      @Override
      @NonNull
      public List<Guest> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLastNameAbbreviation = CursorUtil.getColumnIndexOrThrow(_cursor, "lastNameAbbreviation");
          final int _cursorIndexOfInvitations = CursorUtil.getColumnIndexOrThrow(_cursor, "invitations");
          final int _cursorIndexOfVenueName = CursorUtil.getColumnIndexOrThrow(_cursor, "venueName");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfIsVolunteerBenefit = CursorUtil.getColumnIndexOrThrow(_cursor, "isVolunteerBenefit");
          final int _cursorIndexOfVolunteerId = CursorUtil.getColumnIndexOrThrow(_cursor, "volunteerId");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<Guest> _result = new ArrayList<Guest>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Guest _item;
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
            final int _tmpInvitations;
            _tmpInvitations = _cursor.getInt(_cursorIndexOfInvitations);
            final String _tmpVenueName;
            _tmpVenueName = _cursor.getString(_cursorIndexOfVenueName);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpIsVolunteerBenefit;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsVolunteerBenefit);
            _tmpIsVolunteerBenefit = _tmp != 0;
            final Long _tmpVolunteerId;
            if (_cursor.isNull(_cursorIndexOfVolunteerId)) {
              _tmpVolunteerId = null;
            } else {
              _tmpVolunteerId = _cursor.getLong(_cursorIndexOfVolunteerId);
            }
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new Guest(_tmpId,_tmpSheetsId,_tmpName,_tmpLastNameAbbreviation,_tmpInvitations,_tmpVenueName,_tmpNotes,_tmpIsVolunteerBenefit,_tmpVolunteerId,_tmpLastModified);
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
  public Flow<List<Guest>> getAllGuests() {
    final String _sql = "SELECT * FROM guests ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"guests"}, new Callable<List<Guest>>() {
      @Override
      @NonNull
      public List<Guest> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLastNameAbbreviation = CursorUtil.getColumnIndexOrThrow(_cursor, "lastNameAbbreviation");
          final int _cursorIndexOfInvitations = CursorUtil.getColumnIndexOrThrow(_cursor, "invitations");
          final int _cursorIndexOfVenueName = CursorUtil.getColumnIndexOrThrow(_cursor, "venueName");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfIsVolunteerBenefit = CursorUtil.getColumnIndexOrThrow(_cursor, "isVolunteerBenefit");
          final int _cursorIndexOfVolunteerId = CursorUtil.getColumnIndexOrThrow(_cursor, "volunteerId");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<Guest> _result = new ArrayList<Guest>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Guest _item;
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
            final int _tmpInvitations;
            _tmpInvitations = _cursor.getInt(_cursorIndexOfInvitations);
            final String _tmpVenueName;
            _tmpVenueName = _cursor.getString(_cursorIndexOfVenueName);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpIsVolunteerBenefit;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsVolunteerBenefit);
            _tmpIsVolunteerBenefit = _tmp != 0;
            final Long _tmpVolunteerId;
            if (_cursor.isNull(_cursorIndexOfVolunteerId)) {
              _tmpVolunteerId = null;
            } else {
              _tmpVolunteerId = _cursor.getLong(_cursorIndexOfVolunteerId);
            }
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new Guest(_tmpId,_tmpSheetsId,_tmpName,_tmpLastNameAbbreviation,_tmpInvitations,_tmpVenueName,_tmpNotes,_tmpIsVolunteerBenefit,_tmpVolunteerId,_tmpLastModified);
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
  public Object getGuestById(final long id, final Continuation<? super Guest> $completion) {
    final String _sql = "SELECT * FROM guests WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Guest>() {
      @Override
      @Nullable
      public Guest call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLastNameAbbreviation = CursorUtil.getColumnIndexOrThrow(_cursor, "lastNameAbbreviation");
          final int _cursorIndexOfInvitations = CursorUtil.getColumnIndexOrThrow(_cursor, "invitations");
          final int _cursorIndexOfVenueName = CursorUtil.getColumnIndexOrThrow(_cursor, "venueName");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfIsVolunteerBenefit = CursorUtil.getColumnIndexOrThrow(_cursor, "isVolunteerBenefit");
          final int _cursorIndexOfVolunteerId = CursorUtil.getColumnIndexOrThrow(_cursor, "volunteerId");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final Guest _result;
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
            final int _tmpInvitations;
            _tmpInvitations = _cursor.getInt(_cursorIndexOfInvitations);
            final String _tmpVenueName;
            _tmpVenueName = _cursor.getString(_cursorIndexOfVenueName);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpIsVolunteerBenefit;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsVolunteerBenefit);
            _tmpIsVolunteerBenefit = _tmp != 0;
            final Long _tmpVolunteerId;
            if (_cursor.isNull(_cursorIndexOfVolunteerId)) {
              _tmpVolunteerId = null;
            } else {
              _tmpVolunteerId = _cursor.getLong(_cursorIndexOfVolunteerId);
            }
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _result = new Guest(_tmpId,_tmpSheetsId,_tmpName,_tmpLastNameAbbreviation,_tmpInvitations,_tmpVenueName,_tmpNotes,_tmpIsVolunteerBenefit,_tmpVolunteerId,_tmpLastModified);
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
  public Object getGuestsModifiedAfter(final long timestamp,
      final Continuation<? super List<Guest>> $completion) {
    final String _sql = "SELECT * FROM guests WHERE lastModified > ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, timestamp);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Guest>>() {
      @Override
      @NonNull
      public List<Guest> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLastNameAbbreviation = CursorUtil.getColumnIndexOrThrow(_cursor, "lastNameAbbreviation");
          final int _cursorIndexOfInvitations = CursorUtil.getColumnIndexOrThrow(_cursor, "invitations");
          final int _cursorIndexOfVenueName = CursorUtil.getColumnIndexOrThrow(_cursor, "venueName");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfIsVolunteerBenefit = CursorUtil.getColumnIndexOrThrow(_cursor, "isVolunteerBenefit");
          final int _cursorIndexOfVolunteerId = CursorUtil.getColumnIndexOrThrow(_cursor, "volunteerId");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<Guest> _result = new ArrayList<Guest>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Guest _item;
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
            final int _tmpInvitations;
            _tmpInvitations = _cursor.getInt(_cursorIndexOfInvitations);
            final String _tmpVenueName;
            _tmpVenueName = _cursor.getString(_cursorIndexOfVenueName);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpIsVolunteerBenefit;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsVolunteerBenefit);
            _tmpIsVolunteerBenefit = _tmp != 0;
            final Long _tmpVolunteerId;
            if (_cursor.isNull(_cursorIndexOfVolunteerId)) {
              _tmpVolunteerId = null;
            } else {
              _tmpVolunteerId = _cursor.getLong(_cursorIndexOfVolunteerId);
            }
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new Guest(_tmpId,_tmpSheetsId,_tmpName,_tmpLastNameAbbreviation,_tmpInvitations,_tmpVenueName,_tmpNotes,_tmpIsVolunteerBenefit,_tmpVolunteerId,_tmpLastModified);
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
  public Object getVolunteerBenefitGuests(final Continuation<? super List<Guest>> $completion) {
    final String _sql = "SELECT * FROM guests WHERE isVolunteerBenefit = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Guest>>() {
      @Override
      @NonNull
      public List<Guest> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLastNameAbbreviation = CursorUtil.getColumnIndexOrThrow(_cursor, "lastNameAbbreviation");
          final int _cursorIndexOfInvitations = CursorUtil.getColumnIndexOrThrow(_cursor, "invitations");
          final int _cursorIndexOfVenueName = CursorUtil.getColumnIndexOrThrow(_cursor, "venueName");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfIsVolunteerBenefit = CursorUtil.getColumnIndexOrThrow(_cursor, "isVolunteerBenefit");
          final int _cursorIndexOfVolunteerId = CursorUtil.getColumnIndexOrThrow(_cursor, "volunteerId");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final List<Guest> _result = new ArrayList<Guest>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Guest _item;
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
            final int _tmpInvitations;
            _tmpInvitations = _cursor.getInt(_cursorIndexOfInvitations);
            final String _tmpVenueName;
            _tmpVenueName = _cursor.getString(_cursorIndexOfVenueName);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpIsVolunteerBenefit;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsVolunteerBenefit);
            _tmpIsVolunteerBenefit = _tmp != 0;
            final Long _tmpVolunteerId;
            if (_cursor.isNull(_cursorIndexOfVolunteerId)) {
              _tmpVolunteerId = null;
            } else {
              _tmpVolunteerId = _cursor.getLong(_cursorIndexOfVolunteerId);
            }
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _item = new Guest(_tmpId,_tmpSheetsId,_tmpName,_tmpLastNameAbbreviation,_tmpInvitations,_tmpVenueName,_tmpNotes,_tmpIsVolunteerBenefit,_tmpVolunteerId,_tmpLastModified);
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
  public Object getVolunteerBenefitGuest(final long volunteerId,
      final Continuation<? super Guest> $completion) {
    final String _sql = "SELECT * FROM guests WHERE isVolunteerBenefit = 1 AND volunteerId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, volunteerId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Guest>() {
      @Override
      @Nullable
      public Guest call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLastNameAbbreviation = CursorUtil.getColumnIndexOrThrow(_cursor, "lastNameAbbreviation");
          final int _cursorIndexOfInvitations = CursorUtil.getColumnIndexOrThrow(_cursor, "invitations");
          final int _cursorIndexOfVenueName = CursorUtil.getColumnIndexOrThrow(_cursor, "venueName");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfIsVolunteerBenefit = CursorUtil.getColumnIndexOrThrow(_cursor, "isVolunteerBenefit");
          final int _cursorIndexOfVolunteerId = CursorUtil.getColumnIndexOrThrow(_cursor, "volunteerId");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final Guest _result;
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
            final int _tmpInvitations;
            _tmpInvitations = _cursor.getInt(_cursorIndexOfInvitations);
            final String _tmpVenueName;
            _tmpVenueName = _cursor.getString(_cursorIndexOfVenueName);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpIsVolunteerBenefit;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsVolunteerBenefit);
            _tmpIsVolunteerBenefit = _tmp != 0;
            final Long _tmpVolunteerId;
            if (_cursor.isNull(_cursorIndexOfVolunteerId)) {
              _tmpVolunteerId = null;
            } else {
              _tmpVolunteerId = _cursor.getLong(_cursorIndexOfVolunteerId);
            }
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _result = new Guest(_tmpId,_tmpSheetsId,_tmpName,_tmpLastNameAbbreviation,_tmpInvitations,_tmpVenueName,_tmpNotes,_tmpIsVolunteerBenefit,_tmpVolunteerId,_tmpLastModified);
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
  public Object getGuestBySheetsId(final String sheetsId,
      final Continuation<? super Guest> $completion) {
    final String _sql = "SELECT * FROM guests WHERE sheetsId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sheetsId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Guest>() {
      @Override
      @Nullable
      public Guest call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLastNameAbbreviation = CursorUtil.getColumnIndexOrThrow(_cursor, "lastNameAbbreviation");
          final int _cursorIndexOfInvitations = CursorUtil.getColumnIndexOrThrow(_cursor, "invitations");
          final int _cursorIndexOfVenueName = CursorUtil.getColumnIndexOrThrow(_cursor, "venueName");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfIsVolunteerBenefit = CursorUtil.getColumnIndexOrThrow(_cursor, "isVolunteerBenefit");
          final int _cursorIndexOfVolunteerId = CursorUtil.getColumnIndexOrThrow(_cursor, "volunteerId");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final Guest _result;
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
            final int _tmpInvitations;
            _tmpInvitations = _cursor.getInt(_cursorIndexOfInvitations);
            final String _tmpVenueName;
            _tmpVenueName = _cursor.getString(_cursorIndexOfVenueName);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpIsVolunteerBenefit;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsVolunteerBenefit);
            _tmpIsVolunteerBenefit = _tmp != 0;
            final Long _tmpVolunteerId;
            if (_cursor.isNull(_cursorIndexOfVolunteerId)) {
              _tmpVolunteerId = null;
            } else {
              _tmpVolunteerId = _cursor.getLong(_cursorIndexOfVolunteerId);
            }
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _result = new Guest(_tmpId,_tmpSheetsId,_tmpName,_tmpLastNameAbbreviation,_tmpInvitations,_tmpVenueName,_tmpNotes,_tmpIsVolunteerBenefit,_tmpVolunteerId,_tmpLastModified);
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
  public Object getGuestByName(final String name, final Continuation<? super Guest> $completion) {
    final String _sql = "SELECT * FROM guests WHERE name = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, name);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Guest>() {
      @Override
      @Nullable
      public Guest call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSheetsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sheetsId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfLastNameAbbreviation = CursorUtil.getColumnIndexOrThrow(_cursor, "lastNameAbbreviation");
          final int _cursorIndexOfInvitations = CursorUtil.getColumnIndexOrThrow(_cursor, "invitations");
          final int _cursorIndexOfVenueName = CursorUtil.getColumnIndexOrThrow(_cursor, "venueName");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfIsVolunteerBenefit = CursorUtil.getColumnIndexOrThrow(_cursor, "isVolunteerBenefit");
          final int _cursorIndexOfVolunteerId = CursorUtil.getColumnIndexOrThrow(_cursor, "volunteerId");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "lastModified");
          final Guest _result;
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
            final int _tmpInvitations;
            _tmpInvitations = _cursor.getInt(_cursorIndexOfInvitations);
            final String _tmpVenueName;
            _tmpVenueName = _cursor.getString(_cursorIndexOfVenueName);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpIsVolunteerBenefit;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsVolunteerBenefit);
            _tmpIsVolunteerBenefit = _tmp != 0;
            final Long _tmpVolunteerId;
            if (_cursor.isNull(_cursorIndexOfVolunteerId)) {
              _tmpVolunteerId = null;
            } else {
              _tmpVolunteerId = _cursor.getLong(_cursorIndexOfVolunteerId);
            }
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            _result = new Guest(_tmpId,_tmpSheetsId,_tmpName,_tmpLastNameAbbreviation,_tmpInvitations,_tmpVenueName,_tmpNotes,_tmpIsVolunteerBenefit,_tmpVolunteerId,_tmpLastModified);
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
