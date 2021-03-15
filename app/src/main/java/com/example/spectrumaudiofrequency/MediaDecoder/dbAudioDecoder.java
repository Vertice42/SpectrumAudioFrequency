package com.example.spectrumaudiofrequency.MediaDecoder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class dbAudioDecoder extends SQLiteOpenHelper {
    public static final String TABLE_NAME = "MediaName";
    private static final String SAMPLE_PIECE = "SamplePiece";
    private static final String SAMPLE_DATA = "SAMPLE_DATA";

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    SAMPLE_PIECE + " INTERGER PRIMARY KEY," +
                    SAMPLE_DATA + " BLOB)";

    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + TABLE_NAME;

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "FeedReader.db";

    private final SQLiteDatabase Writable_db;
    private final SQLiteDatabase Readable_db;

    public dbAudioDecoder(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        Writable_db = this.getWritableDatabase();
        Readable_db = this.getReadableDatabase();
    }

    public void addSamplePiece(int SamplePiece, byte[] bytes) {
        ContentValues values = new ContentValues();
        values.put(SAMPLE_PIECE, SamplePiece);
        values.put(SAMPLE_DATA, bytes);
        Writable_db.insert(TABLE_NAME, null, values);
    }

    public void deleteSamplePiece(int SamplePiece) {
        String selection = SAMPLE_PIECE + " = ?";
        String[] selectionArgs = {SamplePiece + ""};

        Readable_db.delete(
                TABLE_NAME,          // The table to query// The array of columns to return (pass null to get all)
                selection,           // The columns for the WHERE clause
                selectionArgs        // The values for the WHERE clause
        );
    }

    public byte[] getSamplePiece(int SamplePiece) {
        String selection = SAMPLE_PIECE + " = ?";
        String[] selectionArgs = {SamplePiece + ""};
        String sortOrder = SAMPLE_PIECE + " DESC";

        Cursor cursor = Readable_db.query(
                TABLE_NAME,             // The table to query
                null,          // The array of columns to return (pass null to get all)
                selection,              // The columns for the WHERE clause
                selectionArgs,          // The values for the WHERE clause
                null,          // don't group the rows
                null,           // don't filter by row groups
                sortOrder               // The sort order
        );
        byte[] blob = null;
        while (cursor.moveToNext()) {
            blob = cursor.getBlob(cursor.getColumnIndex(SAMPLE_DATA));
        }

        cursor.close();
        return blob;
    }

    public void close() {
        Writable_db.close();
        Readable_db.close();
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}


