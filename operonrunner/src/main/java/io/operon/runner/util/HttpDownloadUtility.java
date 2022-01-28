/*
 *   Copyright 2022, operon.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.operon.runner.util;
 
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
 
public class HttpDownloadUtility {

    private static final int BUFFER_SIZE = 4096;
 
    public static String downloadFile(String fileDownloadURL, String folderToSave) throws IOException {
        URL url = new URL(fileDownloadURL);
        
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        
        int responseCode = httpConn.getResponseCode();
        
        String fileName = null;
 
        if (responseCode == HttpURLConnection.HTTP_OK) {
            String contentDisposition = httpConn.getHeaderField("Content-Disposition");
            String contentType = httpConn.getContentType();
            int contentLength = httpConn.getContentLength();
 
            if (contentDisposition != null) {
                int index = contentDisposition.indexOf("filename=");
                if (index > 0) {
                    fileName = contentDisposition.substring(index + 9, contentDisposition.length());
                }
            } else {
                fileName = fileDownloadURL.substring(fileDownloadURL.lastIndexOf("/") + 1, fileDownloadURL.length());
            }

            if (fileName.startsWith("\"")) {
                System.out.println("Removing double-quotes from fileName");
                fileName = fileName.substring(1, fileName.length() - 1); // remove double-quotes
            }

            InputStream inputStream = httpConn.getInputStream();
            String saveFileToFolder = folderToSave + File.separator + fileName;
            OutputStream outputStream = new FileOutputStream(saveFileToFolder);
 
            int bytesRead = -1;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
 
            outputStream.close();
            inputStream.close();
 
            //System.out.println("File downloaded: " + fileName);
        } else {
            System.err.println("Could not download file. Server replied HTTP code: " + responseCode);
        }
        httpConn.disconnect();
        return fileName;
    }
}