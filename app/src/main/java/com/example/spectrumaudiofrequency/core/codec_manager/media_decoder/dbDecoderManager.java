package com.example.spectrumaudiofrequency.core.codec_manager.media_decoder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class dbDecoderManager extends SQLiteOpenHelper {
    private static class DecodedMedias {
        private static final String ID = "ID";
        private static final String TABLE_NAME = "DECODED_MEDIAS";
        private static final String MEDIA_NAME = "MEDIA_NAME";
        private static final String SAMPLE_PEACE_DURATION = "SAMPLE_PEACE_DURATION";
        private static final String TRUE_MEDIA_DURATION = "TRUE_MEDIA_DURATION";
        private static final String IS_COMPLETE = "IS_COMPLETE";

        private static final String SQL_CREATE_ENTRIES =
                "CREATE TABLE " + TABLE_NAME + " (" +
                        ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        MEDIA_NAME + " TEXT NOT NULL, " +
                        SAMPLE_PEACE_DURATION + " REAL ," +
                        TRUE_MEDIA_DURATION + " BIGINT ," +
                        IS_COMPLETE + " INTEGER DEFAULT 0);";

        private static final String SQL_DELETE_ENTRIES =
                "DROP TABLE IF EXISTS " + TABLE_NAME;
    }

    private static class SamplesTable {
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

    public static class MediaSpecs {
        public String MediaName;
        public long TrueMediaDuration;
        public double SampleDuration;

        public MediaSpecs(String mediaName, long trueMediaDuration, double sampleDuration) {
            MediaName = mediaName;
            TrueMediaDuration = trueMediaDuration;
            SampleDuration = sampleDuration;
        }
    }

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "FeedReader.db";

    private final String MediaName;
    private final SQLiteDatabase sqLiteDatabase;

    private int SamplesLength = 0;

    public dbDecoderManager(Context context, String MediaName) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.MediaName = MediaName;
        sqLiteDatabase = this.getWritableDatabase();

        if (!TableExit(MediaName)) createMediaDecoded(MediaName);
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
        String query = "select DISTINCT tbl_name from sqlite_master where tbl_name = '"
                + TableName + "'";
        Cursor cursor = sqLiteDatabase.rawQuery(query, null);

        boolean exit;
        exit = cursor.getCount() > 0;
        cursor.close();
        return exit;
    }

    public boolean MediaIsDecoded(String MediaName) {
        String selection = DecodedMedias.MEDIA_NAME + " = ?";
        String[] selectionArgs = {MediaName + ""};

        Cursor cursor = sqLiteDatabase.query(DecodedMedias.TABLE_NAME, null, selection,
                selectionArgs, null, null, null);

        boolean IsDecoded = false;
        while (cursor.moveToNext())
            IsDecoded = cursor.getInt(cursor.getColumnIndex(DecodedMedias.IS_COMPLETE)) > 0;

        cursor.close();
        return IsDecoded;
    }

    public MediaSpecs getMediaSpecs() {
        String selection = DecodedMedias.MEDIA_NAME + " = ?";
        String[] selectionArgs = {MediaName + ""};

        Cursor cursor = sqLiteDatabase.query(DecodedMedias.TABLE_NAME, null, selection,
                selectionArgs, null, null, null);

        int SampleIdDuration = -1;
        long trueMediaDuration = -1;
        while (cursor.moveToNext()) {
            SampleIdDuration = cursor.getInt(cursor.getColumnIndex(
                    DecodedMedias.SAMPLE_PEACE_DURATION));
            trueMediaDuration = cursor.getInt(cursor.getColumnIndex(
                    DecodedMedias.TRUE_MEDIA_DURATION));
        }

        cursor.close();
        return new MediaSpecs(MediaName, trueMediaDuration, SampleIdDuration);
    }

    private void createMediaDecoded(String MediaName) {
        sqLiteDatabase.execSQL(SamplesTable.getCreateEntries(MediaName));

        ContentValues values = new ContentValues();
        values.put(DecodedMedias.MEDIA_NAME, MediaName);

        sqLiteDatabase.insert(DecodedMedias.TABLE_NAME, null, values);
    }

    public void setDecoded(MediaSpecs mediaSpecs) {
        String selection = DecodedMedias.MEDIA_NAME + " = ?";
        String[] selectionArgs = {mediaSpecs.MediaName + ""};

        ContentValues values = new ContentValues();
        values.put(DecodedMedias.IS_COMPLETE, true);
        values.put(DecodedMedias.SAMPLE_PEACE_DURATION, mediaSpecs.SampleDuration);
        values.put(DecodedMedias.TRUE_MEDIA_DURATION, mediaSpecs.TrueMediaDuration);
        sqLiteDatabase.update(DecodedMedias.TABLE_NAME, values, selection, selectionArgs);
    }

    private Cursor getCursorSamplePiece(long SamplePiece) {
        String selection = SamplesTable.SAMPLE_PIECE + " = ?";
        String[] selectionArgs = {SamplePiece + ""};

        return sqLiteDatabase.query(
                MediaName,          // The table to query
                null,      // The array of columns to return (pass null to get all)
                selection,              // The columns for the WHERE clause
                selectionArgs,          // The values for the WHERE clause
                null,          // don't group the rows
                null,            // don't filter by row groups
                null                // The sort order
        );
    }

    public int getSamplesLength() {
        if (SamplesLength == 0) {
            String selection = SamplesTable.SAMPLE_PIECE + " = ?";
            String[] selectionArgs = {""};

            Cursor query = sqLiteDatabase.query(
                    MediaName,          // The table to query
                    null,      // The array of columns to return (pass null to get all)
                    selection,              // The columns for the WHERE clause
                    selectionArgs,          // The values for the WHERE clause
                    null,          // don't group the rows
                    null,           // don't filter by row groups
                    null           // The sort order
            );
            SamplesLength = query.getCount();
            query.close();
        }
        return SamplesLength;
    }

    public byte[] getSamplePiece(long SamplePiece) {
        Cursor cursor = getCursorSamplePiece(SamplePiece);
        byte[] blob = null;
        if (cursor.getCount() > 0)
            while (cursor.moveToNext())
                blob = cursor.getBlob(cursor.getColumnIndex(SamplesTable.SAMPLE_DATA));
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

    public void addSamplePiece(int PieceSampleTime, byte[] bytes) {
        if (!SampleAlreadyExistOnDataBase(PieceSampleTime)) {//todo ineficiaen al checar exit a cada nova interção
            ContentValues values = new ContentValues();
            values.put(SamplesTable.SAMPLE_PIECE, PieceSampleTime);
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



