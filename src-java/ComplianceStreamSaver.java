package org.crossref.eventdata.twitter;

import java.net.SocketException;
import java.util.Base64;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.HttpClients;
import clojure.lang.IFn;

// Consume a Gnip Compliance Stream into rotating log files. Callback when a file is rotated.
public class ComplianceStreamSaver {
  public void main(String url, String username, String password, IFn rotationCallback, String logLocation, String suffix) throws Exception {
    // True first time.
    boolean reconnect = true;

    // Reconnect on error.
    // There's no catch-up, we just miss some data out.
    // This isn't the end of the world, as we can do bulk spot-checks at any point.
    while (reconnect) {
      RotatingFileWriter fileWriter = new RotatingFileWriter(logLocation, rotationCallback, suffix);

      CloseableHttpClient httpclient = HttpClients.custom().build();
      try {
        HttpGet httpget = new HttpGet(url);
        byte[] credentials = Base64.getEncoder().encode((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        httpget.setHeader("Authorization", "Basic " + new String(credentials, StandardCharsets.UTF_8));

        CloseableHttpResponse response = httpclient.execute(httpget);

        try {
          InputStream inputStream = response.getEntity().getContent();
          BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

          String line = null;
          BufferedWriter writer = null;
          while((line = reader.readLine()) != null) {
            writer = fileWriter.getWriter();
            writer.write(line);
            writer.newLine();
          }

          if (writer != null) {
            writer.close();
          }
        } catch (SocketException ex) {
          // Continue loop.
          reconnect = true;
        } catch (IOException ex) {
          // Includes org.apache.http.TruncatedChunkException
          // Continue loop.
          reconnect = true;

        } catch (Exception ex) {
          System.out.println("Uncaught error! " + ex.toString());
          ex.printStackTrace();
          reconnect = true;
        } finally {
          response.close();
          if (fileWriter != null) {
            fileWriter.close();
          }
        }
      } finally {
        httpclient.close();
        if (fileWriter != null) {
          fileWriter.close();
        }
      }
    }
  }
}