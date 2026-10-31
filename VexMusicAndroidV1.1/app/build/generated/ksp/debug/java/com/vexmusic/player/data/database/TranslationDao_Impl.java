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

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TranslationDao_Impl implements TranslationDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TranslationEntity> __insertionAdapterOfTranslationEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteForTrack;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public TranslationDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTranslationEntity = new EntityInsertionAdapter<TranslationEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `translations` (`trackId`,`targetLanguage`,`sourceHash`,`providerName`,`translatedLines`,`updatedAt`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TranslationEntity entity) {
        statement.bindString(1, entity.getTrackId());
        statement.bindString(2, entity.getTargetLanguage());
        statement.bindLong(3, entity.getSourceHash());
        statement.bindString(4, entity.getProviderName());
        statement.bindString(5, entity.getTranslatedLines());
        statement.bindLong(6, entity.getUpdatedAt());
      }
    };
    this.__preparedStmtOfDeleteForTrack = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM translations WHERE trackId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM translations";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final TranslationEntity entity,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTranslationEntity.insert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteForTrack(final String trackId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteForTrack.acquire();
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
          __preparedStmtOfDeleteForTrack.release(_stmt);
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
  public Object get(final String trackId, final String lang,
      final Continuation<? super TranslationEntity> $completion) {
    final String _sql = "SELECT * FROM translations WHERE trackId = ? AND targetLanguage = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, trackId);
    _argIndex = 2;
    _statement.bindString(_argIndex, lang);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<TranslationEntity>() {
      @Override
      @Nullable
      public TranslationEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTrackId = CursorUtil.getColumnIndexOrThrow(_cursor, "trackId");
          final int _cursorIndexOfTargetLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "targetLanguage");
          final int _cursorIndexOfSourceHash = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceHash");
          final int _cursorIndexOfProviderName = CursorUtil.getColumnIndexOrThrow(_cursor, "providerName");
          final int _cursorIndexOfTranslatedLines = CursorUtil.getColumnIndexOrThrow(_cursor, "translatedLines");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final TranslationEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpTrackId;
            _tmpTrackId = _cursor.getString(_cursorIndexOfTrackId);
            final String _tmpTargetLanguage;
            _tmpTargetLanguage = _cursor.getString(_cursorIndexOfTargetLanguage);
            final int _tmpSourceHash;
            _tmpSourceHash = _cursor.getInt(_cursorIndexOfSourceHash);
            final String _tmpProviderName;
            _tmpProviderName = _cursor.getString(_cursorIndexOfProviderName);
            final String _tmpTranslatedLines;
            _tmpTranslatedLines = _cursor.getString(_cursorIndexOfTranslatedLines);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _result = new TranslationEntity(_tmpTrackId,_tmpTargetLanguage,_tmpSourceHash,_tmpProviderName,_tmpTranslatedLines,_tmpUpdatedAt);
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
  public Object count(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM translations";
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
    final String _sql = "SELECT COALESCE(SUM(LENGTH(translatedLines)), 0) FROM translations";
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
  public Object all(final Continuation<? super List<TranslationEntity>> $completion) {
    final String _sql = "SELECT * FROM translations";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TranslationEntity>>() {
      @Override
      @NonNull
      public List<TranslationEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTrackId = CursorUtil.getColumnIndexOrThrow(_cursor, "trackId");
          final int _cursorIndexOfTargetLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "targetLanguage");
          final int _cursorIndexOfSourceHash = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceHash");
          final int _cursorIndexOfProviderName = CursorUtil.getColumnIndexOrThrow(_cursor, "providerName");
          final int _cursorIndexOfTranslatedLines = CursorUtil.getColumnIndexOrThrow(_cursor, "translatedLines");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final List<TranslationEntity> _result = new ArrayList<TranslationEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TranslationEntity _item;
            final String _tmpTrackId;
            _tmpTrackId = _cursor.getString(_cursorIndexOfTrackId);
            final String _tmpTargetLanguage;
            _tmpTargetLanguage = _cursor.getString(_cursorIndexOfTargetLanguage);
            final int _tmpSourceHash;
            _tmpSourceHash = _cursor.getInt(_cursorIndexOfSourceHash);
            final String _tmpProviderName;
            _tmpProviderName = _cursor.getString(_cursorIndexOfProviderName);
            final String _tmpTranslatedLines;
            _tmpTranslatedLines = _cursor.getString(_cursorIndexOfTranslatedLines);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new TranslationEntity(_tmpTrackId,_tmpTargetLanguage,_tmpSourceHash,_tmpProviderName,_tmpTranslatedLines,_tmpUpdatedAt);
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
