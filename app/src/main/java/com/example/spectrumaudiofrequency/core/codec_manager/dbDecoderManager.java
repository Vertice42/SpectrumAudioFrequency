package com.example.spectrumaudiofrequency.core.codec_manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.media.MediaCodec;

import org.jetbrains.annotations.NotNull;

import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;

public class dbDecoderManager extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "FeedReader.db";
    private final SQLiteDatabase sqLiteDatabase;
    private final MediaDecoder decoder;
    private int NumberOfSamples = 0;

    public dbDecoderManager(Context context, MediaDecoder decoder) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        sqLiteDatabase = this.getWritableDatabase();
        this.decoder = decoder;

        if (!TableExit(decoder.MediaName)) createMediaDecoded(decoder.MediaName);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DecodedMedias.SQL_CREATE_ENTRIES);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
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

    public boolean MediaIsDecoded() {
        String selection = DecodedMedias.MEDIA_NAME + " = ?";
        String[] selectionArgs = {decoder.MediaName + ""};

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
        String[] selectionArgs = {decoder.MediaName};

        Cursor cursor = sqLiteDatabase.query(DecodedMedias.TABLE_NAME, null, selection,
                selectionArgs, null, null, null);

        int SampleIdDuration = -1;
        long trueMediaDuration = -1;
        int SampleSize = -1;
        while (cursor.moveToNext()) {
            SampleSize = cursor.getInt(cursor.getColumnIndex(
                    DecodedMedias.SAMPLE_PEACE_SIZE));
            SampleIdDuration = cursor.getInt(cursor.getColumnIndex(
                    DecodedMedias.SAMPLE_PEACE_DURATION));
            trueMediaDuration = cursor.getInt(cursor.getColumnIndex(
                    DecodedMedias.TRUE_MEDIA_DURATION));

        }

        cursor.close();
        return new MediaSpecs(decoder.MediaName, trueMediaDuration, SampleIdDuration, SampleSize);
    }

    private void createMediaDecoded(String MediaName) {
        sqLiteDatabase.execSQL(SamplesTable.getCreateEntries(MediaName));

        ContentValues values = new ContentValues();
        values.put(DecodedMedias.MEDIA_NAME, MediaName);

        sqLiteDatabase.insert(DecodedMedias.TABLE_NAME, null, values);
    }

    public void setDecoded() {
        String selection = DecodedMedias.MEDIA_NAME + " = ?";
        String[] selectionArgs = {decoder.MediaName};

        MediaSpecs mediaSpecs = new MediaSpecs(decoder.MediaName,
                (long) decoder.getTrueMediaDuration(),
                decoder.SampleDuration,
                decoder.SampleSize);

        ContentValues values = new ContentValues();
        values.put(DecodedMedias.IS_COMPLETE, true);
        values.put(DecodedMedias.SAMPLE_PEACE_DURATION, mediaSpecs.SampleDuration);
        values.put(DecodedMedias.SAMPLE_PEACE_SIZE, mediaSpecs.SampleSize);
        values.put(DecodedMedias.TRUE_MEDIA_DURATION, mediaSpecs.TrueMediaDuration);

        sqLiteDatabase.update(DecodedMedias.TABLE_NAME, values, selection, selectionArgs);
    }

    private Cursor getCursorSamplePiece(long SamplePiece) {
        String selection = SamplesTable.SAMPLE_PIECE + " = ?";
        String[] selectionArgs = {SamplePiece + ""};

        return sqLiteDatabase.query(
                decoder.MediaName,          // The table to query
                null,      // The array of columns to return (pass null to get all)
                selection,              // The columns for the WHERE clause
                selectionArgs,          // The values for the WHERE clause
                null,          // don't group the rows
                null,            // don't filter by row groups
                null                // The sort order
        );
    }

    public int getNumberOfSamples() {
        if (NumberOfSamples == 0) {
            Cursor query = sqLiteDatabase.query(
                    decoder.MediaName,          // The table to query
                    null,      // The array of columns to return (pass null to get all)
                    null,              // The columns for the WHERE clause
                    null,          // The values for the WHERE clause
                    null,          // don't group the rows
                    null,           // don't filter by row groups
                    null           // The sort order
            );
            NumberOfSamples = query.getCount();
            query.close();
        }
        return NumberOfSamples;
    }

    public byte[] getSamplePiece(int Id) {
        Cursor cursor = getCursorSamplePiece(Id);
        byte[] blob = null;
        if (cursor.getCount() > 0)
            while (cursor.moveToNext())
                blob = cursor.getBlob(cursor.getColumnIndex(SamplesTable.SAMPLE_DATA));
        cursor.close();
        return blob;
    }

    public CodecManager.CodecSample getCodecSample(int Id) {
        byte[] sampleBytes = this.getSamplePiece(Id);
        if (sampleBytes == null) return null;
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        bufferInfo.set(0, sampleBytes.length,
                ((long) Id * decoder.SampleDuration),
                BUFFER_FLAG_KEY_FRAME);
        return new CodecManager.CodecSample(bufferInfo, sampleBytes);
    }

    public void clear() {
        String selection = DecodedMedias.MEDIA_NAME + " = ?";
        String[] selectionArgs = {decoder.MediaName};
        sqLiteDatabase.delete(DecodedMedias.TABLE_NAME, selection, selectionArgs);
        sqLiteDatabase.execSQL(SamplesTable.getDeleteEntries(decoder.MediaName));
    }

    public boolean SampleAlreadyExistOnDataBase(long SamplePiece) {
        Cursor cursor = getCursorSamplePiece(SamplePiece);
        boolean exist = cursor.getCount() > 0;
        cursor.close();
        return exist;
    }

    public void add(long PieceSampleTime, byte[] bytes) {
        if (!SampleAlreadyExistOnDataBase(PieceSampleTime)) {//todo ineficiaen al checar exit a cada nova interção
            ContentValues values = new ContentValues();
            values.put(SamplesTable.SAMPLE_PIECE, PieceSampleTime);
            values.put(SamplesTable.SAMPLE_DATA, bytes);
            sqLiteDatabase.insert(decoder.MediaName, null, values);
        }
    }

    public void deleteSamplePiece(int SamplePiece) {
        String selection = SamplesTable.SAMPLE_PIECE + " = ?";
        String[] selectionArgs = {SamplePiece + ""};

        sqLiteDatabase.delete(
                decoder.MediaName,          // The table to query// The array of columns to return (pass null to get all)
                selection,           // The columns for the WHERE clause
                selectionArgs        // The values for the WHERE clause
        );
    }

    public void close() {
        sqLiteDatabase.close();
    }

    private static class DecodedMedias {
        private static final String ID = "ID";
        private static final String TABLE_NAME = "DECODED_MEDIAS";
        private static final String MEDIA_NAME = "MEDIA_NAME";
        private static final String SAMPLE_PEACE_DURATION = "SAMPLE_PEACE_DURATION";
        private static final String SAMPLE_PEACE_SIZE = "SAMPLE_PEACE_SIZE";
        private static final String TRUE_MEDIA_DURATION = "TRUE_MEDIA_DURATION";
        private static final String IS_COMPLETE = "IS_COMPLETE";

        private static final String SQL_CREATE_ENTRIES =
                "CREATE TABLE " + TABLE_NAME + " (" +
                        ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        MEDIA_NAME + " TEXT NOT NULL, " +
                        SAMPLE_PEACE_DURATION + " REAL ," +
                        SAMPLE_PEACE_SIZE + " INTEGER ," +
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
        public int SampleSize;

        public MediaSpecs(String mediaName,
                          long trueMediaDuration,
                          double sampleDuration,
                          int sampleSize) {
            MediaName = mediaName;
            TrueMediaDuration = trueMediaDuration;
            SampleDuration = sampleDuration;
            SampleSize = sampleSize;
        }

        @Override
        public @NotNull String toString() {
            return "MediaSpecs{" +
                    "MediaName='" + MediaName + '\'' +
                    ", TrueMediaDuration=" + TrueMediaDuration +
                    ", SampleDuration=" + SampleDuration +
                    ", SampleSize=" + SampleSize +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MediaSpecs that = (MediaSpecs) o;
            return TrueMediaDuration == that.TrueMediaDuration &&
                    Double.compare(that.SampleDuration, SampleDuration) == 0 &&
                    SampleSize == that.SampleSize &&
                    MediaName.equals(that.MediaName);
        }
    }

}



