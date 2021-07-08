/*
 * This file is part of VanillaGradle, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.gradle.vanilla.internal.util;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.spongepowered.gradle.vanilla.internal.model.GroupArtifactVersion;

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
            out.value(value.toString());
        }

        @Override
        public GroupArtifactVersion read(final JsonReader in) throws IOException {
            final String gav = in.nextString();
            final String[] split = this.split.split(gav, 4);
            if (split.length < 2) {
                throw new IOException("Invalid group:artifact:version string " + gav);
            }
            return GroupArtifactVersion.of(split[0], split[1], split.length > 2 ? split[2] : null);
        }
    }.nullSafe();


    static final TypeAdapter<Pattern> PATTERN = new TypeAdapter<Pattern>() {
        @Override
        public void write(final JsonWriter out, final Pattern value) throws IOException {
            out.value(value.pattern());
        }

        @Override
        public Pattern read(final JsonReader in) throws IOException {
            return Pattern.compile(in.nextString());
        }
    }.nullSafe();

    private GsonSerializers() {}
}
