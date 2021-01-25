package org.spongepowered.vanilla.gradle.util;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

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

    private GsonSerializers() {}
}
