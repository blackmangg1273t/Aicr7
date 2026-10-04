package com.agentos.app.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
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
public final class TaskStepDao_Impl implements TaskStepDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TaskStepEntity> __insertionAdapterOfTaskStepEntity;

  private final EntityDeletionOrUpdateAdapter<TaskStepEntity> __updateAdapterOfTaskStepEntity;

  public TaskStepDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTaskStepEntity = new EntityInsertionAdapter<TaskStepEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `task_steps` (`id`,`taskId`,`index`,`agentName`,`toolName`,`argsJson`,`why`,`status`,`result`,`error`,`startedAt`,`finishedAt`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TaskStepEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getTaskId());
        statement.bindLong(3, entity.getIndex());
        statement.bindString(4, entity.getAgentName());
        if (entity.getToolName() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getToolName());
        }
        statement.bindString(6, entity.getArgsJson());
        statement.bindString(7, entity.getWhy());
        statement.bindString(8, entity.getStatus());
        if (entity.getResult() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getResult());
        }
        if (entity.getError() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getError());
        }
        if (entity.getStartedAt() == null) {
          statement.bindNull(11);
        } else {
          statement.bindLong(11, entity.getStartedAt());
        }
        if (entity.getFinishedAt() == null) {
          statement.bindNull(12);
        } else {
          statement.bindLong(12, entity.getFinishedAt());
        }
      }
    };
    this.__updateAdapterOfTaskStepEntity = new EntityDeletionOrUpdateAdapter<TaskStepEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `task_steps` SET `id` = ?,`taskId` = ?,`index` = ?,`agentName` = ?,`toolName` = ?,`argsJson` = ?,`why` = ?,`status` = ?,`result` = ?,`error` = ?,`startedAt` = ?,`finishedAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TaskStepEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getTaskId());
        statement.bindLong(3, entity.getIndex());
        statement.bindString(4, entity.getAgentName());
        if (entity.getToolName() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getToolName());
        }
        statement.bindString(6, entity.getArgsJson());
        statement.bindString(7, entity.getWhy());
        statement.bindString(8, entity.getStatus());
        if (entity.getResult() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getResult());
        }
        if (entity.getError() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getError());
        }
        if (entity.getStartedAt() == null) {
          statement.bindNull(11);
        } else {
          statement.bindLong(11, entity.getStartedAt());
        }
        if (entity.getFinishedAt() == null) {
          statement.bindNull(12);
        } else {
          statement.bindLong(12, entity.getFinishedAt());
        }
        statement.bindString(13, entity.getId());
      }
    };
  }

  @Override
  public Object insert(final TaskStepEntity s, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTaskStepEntity.insert(s);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final TaskStepEntity s, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfTaskStepEntity.handle(s);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object forTask(final String taskId,
      final Continuation<? super List<TaskStepEntity>> $completion) {
    final String _sql = "SELECT * FROM task_steps WHERE taskId = ? ORDER BY `index` ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, taskId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TaskStepEntity>>() {
      @Override
      @NonNull
      public List<TaskStepEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTaskId = CursorUtil.getColumnIndexOrThrow(_cursor, "taskId");
          final int _cursorIndexOfIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "index");
          final int _cursorIndexOfAgentName = CursorUtil.getColumnIndexOrThrow(_cursor, "agentName");
          final int _cursorIndexOfToolName = CursorUtil.getColumnIndexOrThrow(_cursor, "toolName");
          final int _cursorIndexOfArgsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "argsJson");
          final int _cursorIndexOfWhy = CursorUtil.getColumnIndexOrThrow(_cursor, "why");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfResult = CursorUtil.getColumnIndexOrThrow(_cursor, "result");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAt");
          final int _cursorIndexOfFinishedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "finishedAt");
          final List<TaskStepEntity> _result = new ArrayList<TaskStepEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TaskStepEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTaskId;
            _tmpTaskId = _cursor.getString(_cursorIndexOfTaskId);
            final int _tmpIndex;
            _tmpIndex = _cursor.getInt(_cursorIndexOfIndex);
            final String _tmpAgentName;
            _tmpAgentName = _cursor.getString(_cursorIndexOfAgentName);
            final String _tmpToolName;
            if (_cursor.isNull(_cursorIndexOfToolName)) {
              _tmpToolName = null;
            } else {
              _tmpToolName = _cursor.getString(_cursorIndexOfToolName);
            }
            final String _tmpArgsJson;
            _tmpArgsJson = _cursor.getString(_cursorIndexOfArgsJson);
            final String _tmpWhy;
            _tmpWhy = _cursor.getString(_cursorIndexOfWhy);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpResult;
            if (_cursor.isNull(_cursorIndexOfResult)) {
              _tmpResult = null;
            } else {
              _tmpResult = _cursor.getString(_cursorIndexOfResult);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final Long _tmpStartedAt;
            if (_cursor.isNull(_cursorIndexOfStartedAt)) {
              _tmpStartedAt = null;
            } else {
              _tmpStartedAt = _cursor.getLong(_cursorIndexOfStartedAt);
            }
            final Long _tmpFinishedAt;
            if (_cursor.isNull(_cursorIndexOfFinishedAt)) {
              _tmpFinishedAt = null;
            } else {
              _tmpFinishedAt = _cursor.getLong(_cursorIndexOfFinishedAt);
            }
            _item = new TaskStepEntity(_tmpId,_tmpTaskId,_tmpIndex,_tmpAgentName,_tmpToolName,_tmpArgsJson,_tmpWhy,_tmpStatus,_tmpResult,_tmpError,_tmpStartedAt,_tmpFinishedAt);
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
  public Flow<List<TaskStepEntity>> observeForTask(final String taskId) {
    final String _sql = "SELECT * FROM task_steps WHERE taskId = ? ORDER BY `index` ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, taskId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"task_steps"}, new Callable<List<TaskStepEntity>>() {
      @Override
      @NonNull
      public List<TaskStepEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTaskId = CursorUtil.getColumnIndexOrThrow(_cursor, "taskId");
          final int _cursorIndexOfIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "index");
          final int _cursorIndexOfAgentName = CursorUtil.getColumnIndexOrThrow(_cursor, "agentName");
          final int _cursorIndexOfToolName = CursorUtil.getColumnIndexOrThrow(_cursor, "toolName");
          final int _cursorIndexOfArgsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "argsJson");
          final int _cursorIndexOfWhy = CursorUtil.getColumnIndexOrThrow(_cursor, "why");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfResult = CursorUtil.getColumnIndexOrThrow(_cursor, "result");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAt");
          final int _cursorIndexOfFinishedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "finishedAt");
          final List<TaskStepEntity> _result = new ArrayList<TaskStepEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TaskStepEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTaskId;
            _tmpTaskId = _cursor.getString(_cursorIndexOfTaskId);
            final int _tmpIndex;
            _tmpIndex = _cursor.getInt(_cursorIndexOfIndex);
            final String _tmpAgentName;
            _tmpAgentName = _cursor.getString(_cursorIndexOfAgentName);
            final String _tmpToolName;
            if (_cursor.isNull(_cursorIndexOfToolName)) {
              _tmpToolName = null;
            } else {
              _tmpToolName = _cursor.getString(_cursorIndexOfToolName);
            }
            final String _tmpArgsJson;
            _tmpArgsJson = _cursor.getString(_cursorIndexOfArgsJson);
            final String _tmpWhy;
            _tmpWhy = _cursor.getString(_cursorIndexOfWhy);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpResult;
            if (_cursor.isNull(_cursorIndexOfResult)) {
              _tmpResult = null;
            } else {
              _tmpResult = _cursor.getString(_cursorIndexOfResult);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final Long _tmpStartedAt;
            if (_cursor.isNull(_cursorIndexOfStartedAt)) {
              _tmpStartedAt = null;
            } else {
              _tmpStartedAt = _cursor.getLong(_cursorIndexOfStartedAt);
            }
            final Long _tmpFinishedAt;
            if (_cursor.isNull(_cursorIndexOfFinishedAt)) {
              _tmpFinishedAt = null;
            } else {
              _tmpFinishedAt = _cursor.getLong(_cursorIndexOfFinishedAt);
            }
            _item = new TaskStepEntity(_tmpId,_tmpTaskId,_tmpIndex,_tmpAgentName,_tmpToolName,_tmpArgsJson,_tmpWhy,_tmpStatus,_tmpResult,_tmpError,_tmpStartedAt,_tmpFinishedAt);
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
