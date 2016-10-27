package org.crossref.eventdata.twitter;

import clojure.lang.IFn;
import java.io.File;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

// Provide a Writer interface for writing files. The filename is rotated and a callback (which should be asynchronous) is made when the rotation happens.
// Not threadsafe.
public class RotatingFileWriter {
  // 2 million lines = 300 MB
  private static long MAX_LINES = 2000000;
  private long lines = 0;
  private BufferedWriter writer;
  private String basePath;
  IFn rotateCallback;
  private File file;
  private String suffix;

  public RotatingFileWriter(String basePath, IFn rotateCallback, String suffix) throws IOException {
    this.basePath = basePath;
    this.suffix = suffix;
    this.rotateCallback = rotateCallback;
    this.file = this.newFile();
    this.writer = new BufferedWriter(new FileWriter(this.file));
  }

  private File newFile() {
    long time = System.currentTimeMillis();
    return new File(basePath, Long.toString(time) + this.suffix);
  }

  public BufferedWriter getWriter() throws IOException {
    if (this.lines >= MAX_LINES) {
      this.rotateCallback.invoke(this.file);
      this.writer.close();
      this.lines = 0;

      this.file = this.newFile();
      this.writer = new BufferedWriter(new FileWriter(this.file));
    }

    this.lines++;

    return this.writer;
  } 

  public void close() throws IOException {
    if (this.writer != null) {
      this.writer.close();
      this.writer = null;
    }
  }
}
