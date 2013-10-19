package com.urswolfer.intellij.plugin.gerrit.rest.gson;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * A Gson deserializer which uses UTC as base for dates.
 *
 * I would prefer to use the default Gson parser, but I have found no way to tell Gson that dates are in UTC.
 *
 * @author Urs Wolfer
 */
public class DateDeserializer implements JsonDeserializer<Date> {

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public Date deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        String date = jsonElement.getAsString();
        try {
            return DATE_FORMAT.parse(date);
        } catch (ParseException e) {
            throw new JsonParseException(e);
        }
    }
}
