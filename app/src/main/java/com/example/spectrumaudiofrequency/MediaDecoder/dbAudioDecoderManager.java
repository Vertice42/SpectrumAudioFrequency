package com.example.spectrumaudiofrequency.MediaDecoder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/*
public class dbAudioDecoder{
    static class DecodedMediasTable {
        static final String ID = "ID";
        static final String TABLE_NAME = "DECODED_MEDIAS";
        static final String MEDIA_NAME = "MEDIA_NAME";
        static final String IS_COMPLETE = "IS_COMPLETE";

        static final String SQL_CREATE_ENTRIES =
                "CREATE TABLE " + TABLE_NAME + " (" +
                        ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        MEDIA_NAME + " TEXT NOT NULL, " +
                        IS_COMPLETE + " INTEGER DEFAULT 0);";

        static final String SQL_DELETE_ENTRIES =
                "DROP TABLE IF EXISTS " + TABLE_NAME;
    }

    static class SamplesTable {
        static final String SAMPLE_PIECE = "SAMPLE_PIECE";
        static final String SAMPLE_DATA = "SAMPLE_DATA";

        static String getCreateEntries(String MediaName) {
            return "CREATE TABLE " + MediaName + " (" +
                    SAMPLE_PIECE + " INTERGER PRIMARY KEY," +
                    SAMPLE_DATA + " BLOB )";
        }

        static String getDeleteEntries(String MediaName) {
            return "DROP TABLE IF EXISTS " + MediaName;
        }
    }

    public static class dbAudioDecoderManager {
        private final String MediaName;

        private final SQLiteDatabase Writable_db;

        public dbAudioDecoderManager(String MediaName, SQLiteDatabase writable_db) {
            this.MediaName = MediaName;
            Writable_db = writable_db;
            if (TableExit(MediaName)) deleteMediaDecoded(MediaName);

            createMediaDecoded(MediaName);
        }

        public boolean TableExit(String TableName) {
            String query = "select DISTINCT tbl_name from sqlite_master where tbl_name = '" + TableName + "'";
            Cursor cursor = Writable_db.rawQuery(query, null);

            boolean exit;
            exit = cursor.getCount() > 0;
            cursor.close();
            return exit;
        }

        public void setDecoded(String MediaName) {
            String selection = DecodedMediasTable.MEDIA_NAME + " = ?";
            String[] selectionArgs = {MediaName + ""};

            ContentValues values = new ContentValues();
            values.put(DecodedMediasTable.IS_COMPLETE, true);
            Writable_db.update(DecodedMediasTable.TABLE_NAME, values, selection, selectionArgs);
        }

        public boolean MediaIsDecoded(String MediaName) {
            String selection = DecodedMediasTable.MEDIA_NAME + " = ?";
            String[] selectionArgs = {MediaName + ""};

            Cursor cursor = Writable_db.query(DecodedMediasTable.TABLE_NAME, null, selection, selectionArgs,
                    null, null, null);

            boolean IsDecoded = false;
            while (cursor.moveToNext())
                IsDecoded = cursor.getInt(cursor.getColumnIndex(DecodedMediasTable.IS_COMPLETE)) > 0;

            cursor.close();
            return IsDecoded;
        }

        private void createMediaDecoded(String MediaName) {
            Writable_db.execSQL(SamplesTable.getCreateEntries(MediaName));

            ContentValues values = new ContentValues();
            values.put(DecodedMediasTable.MEDIA_NAME, MediaName);

            Writable_db.insert(DecodedMediasTable.TABLE_NAME, null, values);
        }

        public void deleteMediaDecoded(String MediaName) {
            String selection = DecodedMediasTable.MEDIA_NAME + " = ?";
            String[] selectionArgs = {MediaName + ""};
            Writable_db.delete(DecodedMediasTable.TABLE_NAME, selection, selectionArgs);
            Writable_db.execSQL(SamplesTable.getDeleteEntries(MediaName));
        }

        public void addSamplePiece(int SamplePiece, byte[] bytes) {
            ContentValues values = new ContentValues();
            values.put(SamplesTable.SAMPLE_PIECE, SamplePiece);
            values.put(SamplesTable.SAMPLE_DATA, bytes);
            Writable_db.insert(MediaName, null, values);
        }

        public void deleteSamplePiece(int SamplePiece) {
            String selection = SamplesTable.SAMPLE_PIECE + " = ?";
            String[] selectionArgs = {SamplePiece + ""};

            Writable_db.delete(
                    MediaName,          // The table to query// The array of columns to return (pass null to get all)
                    selection,           // The columns for the WHERE clause
                    selectionArgs        // The values for the WHERE clause
            );
        }

        public byte[] getSamplePiece(int SamplePiece) {
            String selection = SamplesTable.SAMPLE_PIECE + " = ?";
            String[] selectionArgs = {SamplePiece + ""};
            String sortOrder = SamplesTable.SAMPLE_PIECE + " DESC";

            Cursor cursor = Writable_db.query(
                    MediaName,// The table to query
                    null,          // The array of columns to return (pass null to get all)
                    selection,              // The columns for the WHERE clause
                    selectionArgs,          // The values for the WHERE clause
                    null,          // don't group the rows
                    null,            // don't filter by row groups
                    sortOrder                // The sort order
            );
            byte[] blob = null;
            while (cursor.moveToNext()) {
                blob = cursor.getBlob(cursor.getColumnIndex(SamplesTable.SAMPLE_DATA));
            }

            cursor.close();
            return blob;
        }

        public void close() {
            Writable_db.close();
        }
    }

    public static class SQLAudioDecodedDataBase extends SQLiteOpenHelper {
        public static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "FeedReader.db";

        public SQLAudioDecodedDataBase(@Nullable Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        public void onCreate(SQLiteDatabase db) {
            db.execSQL(DecodedMediasTable.SQL_CREATE_ENTRIES);
        }

        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // This database is only a cache for online data, so its upgrade policy is
            // to simply to discard the data and start over
            db.execSQL(DecodedMediasTable.SQL_DELETE_ENTRIES);
            onCreate(db);
        }

        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onUpgrade(db, oldVersion, newVersion);
        }
    }
*/

public class dbAudioDecoderManager extends SQLiteOpenHelper {
    static class DecodedMedias {
        private static final String ID = "ID";
        private static final String TABLE_NAME = "DECODED_MEDIAS";
        private static final String MEDIA_NAME = "MEDIA_NAME";
        private static final String IS_COMPLETE = "IS_COMPLETE";

        private static final String SQL_CREATE_ENTRIES =
                "CREATE TABLE " + TABLE_NAME + " (" +
                        ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        MEDIA_NAME + " TEXT NOT NULL, " +
                        IS_COMPLETE + " INTEGER DEFAULT 0);";

        private static final String SQL_DELETE_ENTRIES =
                "DROP TABLE IF EXISTS " + TABLE_NAME;
    }

    static class SamplesTable {
        private static final String SAMPLE_PIECE = "SAMPLE_PIECE";
        private static final String SAMPLE_DATA = "SAMPLE_DATA";

        static String getCreateEntries(String MediaName) {
            return "CREATE TABLE " + MediaName + " (" +
                    SAMPLE_PIECE + " INTERGER PRIMARY KEY," +
                    SAMPLE_DATA + " BLOB )";
        }

        static String getDeleteEntries(String MediaName) {
            return "DROP TABLE IF EXISTS " + MediaName;
        }
    }

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "FeedReader.db";

    private final String MediaName;
    private final SQLiteDatabase sqLiteDatabase;

    public dbAudioDecoderManager(Context context, String MediaName) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.MediaName = MediaName;
        sqLiteDatabase = this.getWritableDatabase();

        if (TableExit(MediaName)) deleteMediaDecoded(MediaName);

        createMediaDecoded(MediaName);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DecodedMedias.SQL_CREATE_ENTRIES);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        db.execSQL(DecodedMedias.SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public boolean TableExit(String TableName) {
        String query = "select DISTINCT tbl_name from sqlite_master where tbl_name = '" + TableName + "'";
        Cursor cursor = sqLiteDatabase.rawQuery(query, null);

        boolean exit;
        exit = cursor.getCount() > 0;
        cursor.close();
        return exit;
    }

    public boolean MediaIsDecoded(String MediaName) {
        String selection = DecodedMedias.MEDIA_NAME + " = ?";
        String[] selectionArgs = {MediaName + ""};

        Cursor cursor = sqLiteDatabase.query(DecodedMedias.TABLE_NAME, null, selection, selectionArgs,
                null, null, null);

        boolean IsDecoded = false;
        while (cursor.moveToNext())
            IsDecoded = cursor.getInt(cursor.getColumnIndex(DecodedMedias.IS_COMPLETE)) > 0;

        cursor.close();
        return IsDecoded;
    }

    private void createMediaDecoded(String MediaName) {
        sqLiteDatabase.execSQL(SamplesTable.getCreateEntries(MediaName));

        ContentValues values = new ContentValues();
        values.put(DecodedMedias.MEDIA_NAME, MediaName);

        sqLiteDatabase.insert(DecodedMedias.TABLE_NAME, null, values);
    }

    public void setDecoded(String MediaName) {
        String selection = DecodedMedias.MEDIA_NAME + " = ?";
        String[] selectionArgs = {MediaName + ""};

        ContentValues values = new ContentValues();
        values.put(DecodedMedias.IS_COMPLETE, true);
        sqLiteDatabase.update(DecodedMedias.TABLE_NAME, values, selection, selectionArgs);
    }

    private Cursor getCursorSamplePiece(int SamplePiece){
        String selection = SamplesTable.SAMPLE_PIECE + " = ?";
        String[] selectionArgs = {SamplePiece + ""};
        String sortOrder = SamplesTable.SAMPLE_PIECE + " DESC";

        return sqLiteDatabase.query(
                MediaName,          // The table to query
                null,      // The array of columns to return (pass null to get all)
                selection,              // The columns for the WHERE clause
                selectionArgs,          // The values for the WHERE clause
                null,          // don't group the rows
                null,            // don't filter by row groups
                sortOrder                // The sort order
        );
    }

    public byte[] getSamplePiece(int SamplePiece) {
        Cursor cursor = getCursorSamplePiece(SamplePiece);
        byte[] blob = null;
        while (cursor.moveToNext()) {
            blob = cursor.getBlob(cursor.getColumnIndex(SamplesTable.SAMPLE_DATA));
        }

        cursor.close();
        return blob;
    }

    public void deleteMediaDecoded(String MediaName) {
        String selection = DecodedMedias.MEDIA_NAME + " = ?";
        String[] selectionArgs = {MediaName + ""};
        sqLiteDatabase.delete(DecodedMedias.TABLE_NAME, selection, selectionArgs);
        sqLiteDatabase.execSQL(SamplesTable.getDeleteEntries(MediaName));
    }

    public boolean SampleAlreadyExistOnDataBase(int SamplePiece) {
        Cursor cursor = getCursorSamplePiece(SamplePiece);
        boolean exist = cursor.getCount() > 0;
        cursor.close();
        return exist;
    }

    public void addSamplePiece(int SampleTime, byte[] bytes) {
        if (!SampleAlreadyExistOnDataBase(SampleTime)) {//todo ineficiaen al checar exit a cada nova interção
            ContentValues values = new ContentValues();
            values.put(SamplesTable.SAMPLE_PIECE, SampleTime);
            values.put(SamplesTable.SAMPLE_DATA, bytes);
            sqLiteDatabase.insert(MediaName, null, values);
        }
    }

    public void deleteSamplePiece(int SamplePiece) {
        String selection = SamplesTable.SAMPLE_PIECE + " = ?";
        String[] selectionArgs = {SamplePiece + ""};

        sqLiteDatabase.delete(
                MediaName,          // The table to query// The array of columns to return (pass null to get all)
                selection,           // The columns for the WHERE clause
                selectionArgs        // The values for the WHERE clause
        );
    }

    public void close() {
        sqLiteDatabase.close();
    }

}



