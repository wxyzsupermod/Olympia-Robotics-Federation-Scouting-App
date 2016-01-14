package frcteam4450.frcscouting;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Created by wxyzs on 1/13/2016.
 */
public class Save {
    private static final int DATABASE_VERSION = 1;
    public static final String SCOUTING_TABLE_NAME = "scouting";
    public static final String SCOUTING_TABLE_CREATE = "CREATE TABLE `" + SCOUTING_TABLE_NAME + "` (`match_number` INT(4) NOT NULL, `team_number` INT(4) NOT NULL, `alliance` BOOLEAN NOT NULL DEFAULT FALSE, `teleop` BOOLEAN NOT NULL, `moat_crossed` INT(2) NOT NULL DEFAULT 0, `moat_failed` INT(2) NOT NULL DEFAULT 0, `portcullis_crossed` INT(2) NOT NULL DEFAULT 0, `portcullis_failed` INT(2) NOT NULL DEFAULT 0, `sallyport_crossed` INT(2) NOT NULL DEFAULT 0, `sallyport_failed` INT(2) NOT NULL DEFAULT 0, `ramparts_crossed` INT(2) NOT NULL DEFAULT 0, `ramparts_failed` INT(2) NOT NULL DEFAULT 0, `chivaldefrise_crossed` INT(2) NOT NULL DEFAULT 0, `chivaldefrise_failed` INT(2) NOT NULL DEFAULT 0, `rockwall_crossed` INT(2) NOT NULL DEFAULT 0, `rockwall_failed` INT(2) NOT NULL DEFAULT 0, `roughterrain_crossed` INT(2) NOT NULL DEFAULT 0, `roughterrain_failed` INT(2) NOT NULL DEFAULT 0, `lowbar_crossed` INT(2) NOT NULL DEFAULT 0, `lowbar_failed` INT(2) NOT NULL DEFAULT 0, `lowgoalleft_capable` BOOLEAN NOT NULL DEFAULT FALSE, `lowgoalleft_scored` INT(2) NOT NULL DEFAULT 0, `lowgoalleft_missed` INT(2) NOT NULL DEFAULT 0, `lowgoalright_capable` BOOLEAN NOT NULL DEFAULT FALSE, `lowgoalright_scored` INT(2) NOT NULL DEFAULT 0, `lowgoalright_missed` INT(2) NOT NULL DEFAULT 0, `highgoalleft_capable` BOOLEAN NOT NULL DEFAULT FALSE, `highgoalleft_scored` INT(2) NOT NULL DEFAULT 0, `highgoalleft_missed` INT(2) NOT NULL DEFAULT 0, `highgoalcenter_capable` BOOLEAN NOT NULL DEFAULT FALSE, `highgoalcenter_scored` INT(2) NOT NULL DEFAULT 0, `highgoalcenter_missed` INT(2) NOT NULL DEFAULT 0, `highgoalright_capable` BOOLEAN NOT NULL DEFAULT FALSE, `highgoalright_scored` INT(2) NOT NULL DEFAULT 0, `highgoalright_missed` INT(2) NOT NULL DEFAULT 0)";

    protected ScouterDatabase(Context context) {
        super(context, "com.orf4450.Scouter_DB", null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SCOUTING_TABLE_CREATE);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        //db.execSQL("DROP TABLE IF EXISTS " + SCOUTING_TABLE_NAME);
        //onCreate(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public int getLastMatchNumber() {
        SQLiteDatabase db = getReadableDatabase();
        // Fetch the greatest match number
        Cursor cursor = db.rawQuery("SELECT `match_number` FROM `" + SCOUTING_TABLE_NAME
                + "` ORDER BY `match_number` DESC LIMIT 1", null);
        int last_num = 0;
        if (cursor.moveToNext()) {
            last_num = cursor.getInt(0);
        }
        cursor.close();
        db.close();
        return last_num;
    }

    public HashMap<String, HashMap<String, Object>> loadMatch(int match_number, int team_number) {
        HashMap<String, HashMap<String, Object>> bundle = new HashMap<>(2);
        bundle.put("autonomous", loadMatch(match_number, team_number, false));
        bundle.put("teleop", loadMatch(match_number, team_number, true));
        return bundle;
    }

    public void deleteMatch(int match_number, int team_number) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL(String.format("DELETE FROM `" + SCOUTING_TABLE_NAME
                + "` WHERE `match_number`=%1$s AND `team_number`=%2$s", match_number, team_number));
        db.close();
    }

    public void deleteAllData() {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM `" + SCOUTING_TABLE_NAME + "`");
    }

    public LinkedList<MatchDescriptor> getStoredMatchList() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT `match_number`, `team_number` FROM `" + SCOUTING_TABLE_NAME
                + "` WHERE `teleop`=0 ORDER BY `match_number` ASC", null);
        LinkedList<MatchDescriptor> list = new LinkedList<>();
        while (cursor.moveToNext()) {
            list.add(new MatchDescriptor(cursor.getInt(0), cursor.getInt(1)));
        }
        cursor.close();
        db.close();
        return list;
    }

    private HashMap<String, Object> loadMatch(int match_number, int team_number, boolean teleop) {
        SQLiteDatabase db = getReadableDatabase();
        // We can't use normal bindings here because they are bound as Strings
        String raw_query = String.format("SELECT * FROM `" + SCOUTING_TABLE_NAME
                        + "` WHERE `match_number`=%1$s AND `team_number`=%2$s AND `teleop`=%3$s", match_number,
                team_number, teleop ? 1 : 0);
        // Execute the query
        Cursor cursor = db.rawQuery(raw_query, null);
        HashMap<String, Object> bundle = new HashMap<>(cursor.getColumnCount());
        if (cursor.moveToNext()) {
            // Load each column/value pair into the bundle
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                String column_name = cursor.getColumnName(i);
                switch (cursor.getType(i)) {
                    case Cursor.FIELD_TYPE_BLOB:
                        bundle.put(column_name, cursor.getBlob(i));
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        bundle.put(column_name, cursor.getDouble(i));
                        break;
                    case Cursor.FIELD_TYPE_INTEGER:
                        bundle.put(column_name, cursor.getInt(i));
                        break;
                    case Cursor.FIELD_TYPE_NULL:
                        bundle.put(column_name, null);
                        break;
                    case Cursor.FIELD_TYPE_STRING:
                        bundle.put(column_name, cursor.getString(i));
                        break;
                }
            }
        }
        cursor.close();
        db.close();
        bundle.put("teleop", teleop ? 1 : 0);
        return bundle;
    }

    public void saveMatch(HashMap<String, HashMap<String, Object>> data_bundle) {
        saveMatch(data_bundle.get("autonomous"), false);
        saveMatch(data_bundle.get("teleop"), true);
    }

    public void saveMatch(HashMap<String, Object> bundle, boolean teleop) {
        if (bundle == null || bundle.isEmpty()) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        // Delete already-existing rows for this match/team/mode combination
        db.execSQL("DELETE FROM `" + SCOUTING_TABLE_NAME + "` WHERE `match_number`=? AND `team_number`=? AND `teleop`=?",
                new Object[]{bundle.get("match_number"), bundle.get("team_number"), teleop});
        // Begin building the INSERT statement
        StringBuilder query_builder = new StringBuilder("INSERT INTO `").append(SCOUTING_TABLE_NAME).append("` (");
        LinkedList<Object> bindargs = new LinkedList<>();
        // Add an argument for each key/value pair of the bundle
        for (String key : bundle.keySet()) {
            query_builder.append("`").append(key).append("`, ");
            bindargs.add(bundle.get(key));
        }
        // Get rid of that pesky trailing comma
        query_builder.delete(query_builder.length() - 2, query_builder.length());
        query_builder.append(") VALUES (");
        Object[] bindargs_array = bindargs.toArray();
        for (int i = 0; i < bindargs_array.length; i++) {
            query_builder.append("?");
            if (i < bindargs_array.length - 1) {
                query_builder.append(", ");
            }
        }
        query_builder.append(")");
        String raw_query = query_builder.toString();
        // Execute the statement
        db.execSQL(raw_query, bindargs_array);
        db.close();
    }

    public void upload(OutputStream out) throws IOException {
        LinkedList<MatchDescriptor> descriptors = getStoredMatchList();
        LinkedList<HashMap<String, HashMap<String, Object>>> for_serialization = new LinkedList<>();
        for (MatchDescriptor descriptor : descriptors) {
            for_serialization.add(loadMatch(descriptor.getMatchNumber(), descriptor.getTeamNumber()));
        }
        NuggetSerializable<LinkedList<HashMap<String, HashMap<String, Object>>>> nugget = new NuggetSerializable<>("", for_serialization);
        Nugget.writeNugget(nugget, new DataOutputStream(out));
        out.flush();
    }
}
