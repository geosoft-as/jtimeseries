package no.petroware.logio.json;

import java.io.InputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.File;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.List;

import javax.json.JsonReader;
import javax.json.Json;
import javax.json.JsonObject;

import org.leadpony.justify.api.JsonSchema;
import org.leadpony.justify.api.JsonValidationService;
import org.leadpony.justify.api.ProblemHandler;
import org.leadpony.justify.api.Problem;

/*TODO*/ final class JsonWellLogFormatSchema
{
  private static final EventHandler eventHandler_ = new EventHandler();

  public JsonWellLogFormatSchema()
  {
  }

  public static URL getLocation()
  {
    try {
      return new URL("https://jsonwelllogformat.org/schemas/JsonWellLogFormat.json");
    }
    catch (MalformedURLException exception) {
      assert false : "This will never happen";
      return null;
    }
  }

  private static JsonSchema loadSchema()
    throws IOException
  {
    URL url = getLocation();

    JsonValidationService validationService = JsonValidationService.newInstance();

    InputStream inputStream = null;

    try {
      inputStream = url.openStream();
      JsonSchema schema = validationService.readSchema(inputStream);
      return schema;
    }
    finally {
      inputStream.close();
    }
  }

  public static void validate(InputStream inputStream)
    throws IOException
  {
    JsonSchema schema = loadSchema();
    JsonValidationService validationService = JsonValidationService.newInstance();

    JsonReader reader = validationService.createReader(inputStream , schema, eventHandler_);
    reader.read();
  }



  public static String getSchema(JsonLog log)
  {
    return "Hei";
  }


  private static class EventHandler implements ProblemHandler
  {
    @Override
    public void handleProblems(List<Problem> problems)
    {
      for (Problem problem : problems)
        System.out.println(problem);
    }
  }

  public static void main(String[] arguments)
  {
    try {
      JsonWellLogFormatSchema.validate(new FileInputStream(new File("C:/Users/main/logdata/json/WLC_COMPOSITE_1.json")));
    }
    catch (IOException exception) {
      exception.printStackTrace();
    }
  }
}
