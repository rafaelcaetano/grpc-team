package com.pdz.team;

import com.google.protobuf.util.JsonFormat;
import com.pdz.team.dto.FeatureDatabase;
import com.pdz.team.dto.Team;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;

public class TeamUtils {

    public static List<Team> parseTeam(URL file) throws IOException {
        InputStream input = file.openStream();
        try {
            Reader reader = new InputStreamReader(input, Charset.forName("UTF-8"));
            try {
                FeatureDatabase.Builder database = FeatureDatabase.newBuilder();
                JsonFormat.parser().merge(reader, database);
                return database.getTeamList();
            } finally {
                reader.close();
            }
        } finally {
            input.close();
        }
    }

    public static URL getDefaultFeaturesFile() {
        return TeamServer.class.getResource("team_db.json");
    }
}
