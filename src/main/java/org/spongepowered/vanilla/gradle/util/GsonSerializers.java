package org.spongepowered.vanilla.gradle.util;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.spongepowered.vanilla.gradle.model.GroupArtifactVersion;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public final class GsonSerializers {

    public static final JsonDeserializer<ZonedDateTime> ZDT = (json, typeOfT, context) -> {
        final JsonPrimitive jsonPrimitive = json.getAsJsonPrimitive();
        try {

            // if provided as String - '2011-12-03T10:15:30+01:00[Europe/Paris]'
            if (jsonPrimitive.isString()){
                return ZonedDateTime.parse(jsonPrimitive.getAsString(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
            }

            // if provided as Long
            if (jsonPrimitive.isNumber()){
                return ZonedDateTime.ofInstant(Instant.ofEpochMilli(jsonPrimitive.getAsLong()), ZoneId.systemDefault());
            }

        } catch(final RuntimeException e){
            throw new JsonParseException("Unable to parse ZonedDateTime", e);
        }
        throw new JsonParseException("Unable to parse ZonedDateTime");
    };

    public static final TypeAdapter<GroupArtifactVersion> GAV = new TypeAdapter<GroupArtifactVersion>() {
        private final Pattern split = Pattern.compile(":", Pattern.LITERAL);

        @Override
        public void write(final JsonWriter out, final GroupArtifactVersion value) throws IOException {
            throw new UnsupportedOperationException("unnecessary");
        }

        @Override
        public GroupArtifactVersion read(final JsonReader in) throws IOException {
            final String gav = in.nextString();
            final String[] split = this.split.split(gav, 4);
            if (split.length < 2) {
                throw new IOException("Invalid group:artifact:version string " + gav);
            }
            return new GroupArtifactVersion(split[0], split[1], split.length > 2 ? split[2] : null);
        }
    }.nullSafe();

    private GsonSerializers() {}
}
