package com.vexmusic.player.data.database;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
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
public final class LyricsDao_Impl implements LyricsDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<LyricsEntity> __insertionAdapterOfLyricsEntity;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public LyricsDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfLyricsEntity = new EntityInsertionAdapter<LyricsEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `lyrics` (`trackId`,`providerId`,`providerName`,`resultId`,`content`,`format`,`syncType`,`language`,`userSelected`,`userEdited`,`offsetMs`,`updatedAt`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LyricsEntity entity) {
        statement.bindString(1, entity.getTrackId());
        statement.bindString(2, entity.getProviderId());
        statement.bindString(3, entity.getProviderName());
        statement.bindString(4, entity.getResultId());
        statement.bindString(5, entity.getContent());
        statement.bindString(6, entity.getFormat());
        statement.bindString(7, entity.getSyncType());
        if (entity.getLanguage() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getLanguage());
        }
        final int _tmp = entity.getUserSelected() ? 1 : 0;
        statement.bindLong(9, _tmp);
        final int _tmp_1 = entity.getUserEdited() ? 1 : 0;
        statement.bindLong(10, _tmp_1);
        statement.bindLong(11, entity.getOffsetMs());
        statement.bindLong(12, entity.getUpdatedAt());
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM lyrics WHERE trackId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM lyrics";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final LyricsEntity entity, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLyricsEntity.insert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final String trackId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, trackId);
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
          __preparedStmtOfDelete.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
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
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object get(final String trackId, final Continuation<? super LyricsEntity> $completion) {
    final String _sql = "SELECT * FROM lyrics WHERE trackId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, trackId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LyricsEntity>() {
      @Override
      @Nullable
      public LyricsEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTrackId = CursorUtil.getColumnIndexOrThrow(_cursor, "trackId");
          final int _cursorIndexOfProviderId = CursorUtil.getColumnIndexOrThrow(_cursor, "providerId");
          final int _cursorIndexOfProviderName = CursorUtil.getColumnIndexOrThrow(_cursor, "providerName");
          final int _cursorIndexOfResultId = CursorUtil.getColumnIndexOrThrow(_cursor, "resultId");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfFormat = CursorUtil.getColumnIndexOrThrow(_cursor, "format");
          final int _cursorIndexOfSyncType = CursorUtil.getColumnIndexOrThrow(_cursor, "syncType");
          final int _cursorIndexOfLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "language");
          final int _cursorIndexOfUserSelected = CursorUtil.getColumnIndexOrThrow(_cursor, "userSelected");
          final int _cursorIndexOfUserEdited = CursorUtil.getColumnIndexOrThrow(_cursor, "userEdited");
          final int _cursorIndexOfOffsetMs = CursorUtil.getColumnIndexOrThrow(_cursor, "offsetMs");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final LyricsEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpTrackId;
            _tmpTrackId = _cursor.getString(_cursorIndexOfTrackId);
            final String _tmpProviderId;
            _tmpProviderId = _cursor.getString(_cursorIndexOfProviderId);
            final String _tmpProviderName;
            _tmpProviderName = _cursor.getString(_cursorIndexOfProviderName);
            final String _tmpResultId;
            _tmpResultId = _cursor.getString(_cursorIndexOfResultId);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final String _tmpFormat;
            _tmpFormat = _cursor.getString(_cursorIndexOfFormat);
            final String _tmpSyncType;
            _tmpSyncType = _cursor.getString(_cursorIndexOfSyncType);
            final String _tmpLanguage;
            if (_cursor.isNull(_cursorIndexOfLanguage)) {
              _tmpLanguage = null;
            } else {
              _tmpLanguage = _cursor.getString(_cursorIndexOfLanguage);
            }
            final boolean _tmpUserSelected;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfUserSelected);
            _tmpUserSelected = _tmp != 0;
            final boolean _tmpUserEdited;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfUserEdited);
            _tmpUserEdited = _tmp_1 != 0;
            final long _tmpOffsetMs;
            _tmpOffsetMs = _cursor.getLong(_cursorIndexOfOffsetMs);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _result = new LyricsEntity(_tmpTrackId,_tmpProviderId,_tmpProviderName,_tmpResultId,_tmpContent,_tmpFormat,_tmpSyncType,_tmpLanguage,_tmpUserSelected,_tmpUserEdited,_tmpOffsetMs,_tmpUpdatedAt);
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
  public Flow<LyricsEntity> observe(final String trackId) {
    final String _sql = "SELECT * FROM lyrics WHERE trackId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, trackId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"lyrics"}, new Callable<LyricsEntity>() {
      @Override
      @Nullable
      public LyricsEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTrackId = CursorUtil.getColumnIndexOrThrow(_cursor, "trackId");
          final int _cursorIndexOfProviderId = CursorUtil.getColumnIndexOrThrow(_cursor, "providerId");
          final int _cursorIndexOfProviderName = CursorUtil.getColumnIndexOrThrow(_cursor, "providerName");
          final int _cursorIndexOfResultId = CursorUtil.getColumnIndexOrThrow(_cursor, "resultId");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfFormat = CursorUtil.getColumnIndexOrThrow(_cursor, "format");
          final int _cursorIndexOfSyncType = CursorUtil.getColumnIndexOrThrow(_cursor, "syncType");
          final int _cursorIndexOfLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "language");
          final int _cursorIndexOfUserSelected = CursorUtil.getColumnIndexOrThrow(_cursor, "userSelected");
          final int _cursorIndexOfUserEdited = CursorUtil.getColumnIndexOrThrow(_cursor, "userEdited");
          final int _cursorIndexOfOffsetMs = CursorUtil.getColumnIndexOrThrow(_cursor, "offsetMs");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final LyricsEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpTrackId;
            _tmpTrackId = _cursor.getString(_cursorIndexOfTrackId);
            final String _tmpProviderId;
            _tmpProviderId = _cursor.getString(_cursorIndexOfProviderId);
            final String _tmpProviderName;
            _tmpProviderName = _cursor.getString(_cursorIndexOfProviderName);
            final String _tmpResultId;
            _tmpResultId = _cursor.getString(_cursorIndexOfResultId);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final String _tmpFormat;
            _tmpFormat = _cursor.getString(_cursorIndexOfFormat);
            final String _tmpSyncType;
            _tmpSyncType = _cursor.getString(_cursorIndexOfSyncType);
            final String _tmpLanguage;
            if (_cursor.isNull(_cursorIndexOfLanguage)) {
              _tmpLanguage = null;
            } else {
              _tmpLanguage = _cursor.getString(_cursorIndexOfLanguage);
            }
            final boolean _tmpUserSelected;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfUserSelected);
            _tmpUserSelected = _tmp != 0;
            final boolean _tmpUserEdited;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfUserEdited);
            _tmpUserEdited = _tmp_1 != 0;
            final long _tmpOffsetMs;
            _tmpOffsetMs = _cursor.getLong(_cursorIndexOfOffsetMs);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _result = new LyricsEntity(_tmpTrackId,_tmpProviderId,_tmpProviderName,_tmpResultId,_tmpContent,_tmpFormat,_tmpSyncType,_tmpLanguage,_tmpUserSelected,_tmpUserEdited,_tmpOffsetMs,_tmpUpdatedAt);
          } else {
            _result = null;
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
  public Object count(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM lyrics";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
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
  public Object totalChars(final Continuation<? super Long> $completion) {
    final String _sql = "SELECT COALESCE(SUM(LENGTH(content)), 0) FROM lyrics";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Long _result;
          if (_cursor.moveToFirst()) {
            final long _tmp;
            _tmp = _cursor.getLong(0);
            _result = _tmp;
          } else {
            _result = 0L;
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
  public Object all(final Continuation<? super List<LyricsEntity>> $completion) {
    final String _sql = "SELECT * FROM lyrics";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LyricsEntity>>() {
      @Override
      @NonNull
      public List<LyricsEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTrackId = CursorUtil.getColumnIndexOrThrow(_cursor, "trackId");
          final int _cursorIndexOfProviderId = CursorUtil.getColumnIndexOrThrow(_cursor, "providerId");
          final int _cursorIndexOfProviderName = CursorUtil.getColumnIndexOrThrow(_cursor, "providerName");
          final int _cursorIndexOfResultId = CursorUtil.getColumnIndexOrThrow(_cursor, "resultId");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfFormat = CursorUtil.getColumnIndexOrThrow(_cursor, "format");
          final int _cursorIndexOfSyncType = CursorUtil.getColumnIndexOrThrow(_cursor, "syncType");
          final int _cursorIndexOfLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "language");
          final int _cursorIndexOfUserSelected = CursorUtil.getColumnIndexOrThrow(_cursor, "userSelected");
          final int _cursorIndexOfUserEdited = CursorUtil.getColumnIndexOrThrow(_cursor, "userEdited");
          final int _cursorIndexOfOffsetMs = CursorUtil.getColumnIndexOrThrow(_cursor, "offsetMs");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final List<LyricsEntity> _result = new ArrayList<LyricsEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LyricsEntity _item;
            final String _tmpTrackId;
            _tmpTrackId = _cursor.getString(_cursorIndexOfTrackId);
            final String _tmpProviderId;
            _tmpProviderId = _cursor.getString(_cursorIndexOfProviderId);
            final String _tmpProviderName;
            _tmpProviderName = _cursor.getString(_cursorIndexOfProviderName);
            final String _tmpResultId;
            _tmpResultId = _cursor.getString(_cursorIndexOfResultId);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final String _tmpFormat;
            _tmpFormat = _cursor.getString(_cursorIndexOfFormat);
            final String _tmpSyncType;
            _tmpSyncType = _cursor.getString(_cursorIndexOfSyncType);
            final String _tmpLanguage;
            if (_cursor.isNull(_cursorIndexOfLanguage)) {
              _tmpLanguage = null;
            } else {
              _tmpLanguage = _cursor.getString(_cursorIndexOfLanguage);
            }
            final boolean _tmpUserSelected;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfUserSelected);
            _tmpUserSelected = _tmp != 0;
            final boolean _tmpUserEdited;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfUserEdited);
            _tmpUserEdited = _tmp_1 != 0;
            final long _tmpOffsetMs;
            _tmpOffsetMs = _cursor.getLong(_cursorIndexOfOffsetMs);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new LyricsEntity(_tmpTrackId,_tmpProviderId,_tmpProviderName,_tmpResultId,_tmpContent,_tmpFormat,_tmpSyncType,_tmpLanguage,_tmpUserSelected,_tmpUserEdited,_tmpOffsetMs,_tmpUpdatedAt);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
