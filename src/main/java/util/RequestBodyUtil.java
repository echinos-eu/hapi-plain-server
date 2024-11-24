package util;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class RequestBodyUtil {

  public static byte[] getRequestBodyAsByteArray(HttpServletRequest request) throws IOException {
    try (InputStream inputStream = request.getInputStream();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

      byte[] buffer = new byte[1024];
      int bytesRead;

      while ((bytesRead = inputStream.read(buffer)) != -1) {
        byteArrayOutputStream.write(buffer, 0, bytesRead);
      }

      return byteArrayOutputStream.toByteArray();
    }
  }
}
