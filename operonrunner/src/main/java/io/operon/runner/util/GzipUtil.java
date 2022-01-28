/** OPERON-LICENSE **/
package io.operon.runner.util;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.Arrays;

public class GzipUtil {
    
    public static byte[] compress(final byte[] bytes) throws IOException {
      if ((bytes == null) || (bytes.length == 0)) {
        return null;
      }
      ByteArrayOutputStream obj = new ByteArrayOutputStream();
      GZIPOutputStream gzip = new GZIPOutputStream(obj);
      gzip.write(bytes);
      gzip.flush();
      gzip.close();
      return obj.toByteArray();
    }
    
    public static byte[] decompress(final byte[] compressed) throws IOException {
        if ((compressed == null) || (compressed.length == 0)) {
          return "".getBytes();
        }
        byte[] tempResult = new byte[compressed.length];
        final GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed));
        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gis));
  
        int c = 0;
        int i = 0;

        while((c = bufferedReader.read()) != -1 && i < compressed.length) {
          tempResult[i++] = (byte) c;
        }
        
        // Copy from allocated tempResult the amount of bytes that was actually written:
        byte[] result = Arrays.copyOf(tempResult, i);

        return result;
    }
    
    public static boolean isCompressed(final byte[] compressed) {
        return (compressed[0] == (byte) (GZIPInputStream.GZIP_MAGIC)) 
          && (compressed[1] == (byte) (GZIPInputStream.GZIP_MAGIC >> 8));
    }
    
}