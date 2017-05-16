/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3.internal.http;

import java.net.HttpURLConnection;
import java.net.Proxy;
import okhttp3.HttpUrl;
import okhttp3.Request;

public final class RequestLine {
  private RequestLine() {
  }

  /**
   * 返回一个request的状态行，就像是"GET / HTTP/1.1". 这是通过{@link HttpURLConnection#getHeaderFields}
   * 暴露给应用的API。所以这里需要去设置，甚至是使用HTTP/2进行传输
   * Returns the request status line, like "GET / HTTP/1.1". This is exposed to the application by
   * {@link HttpURLConnection#getHeaderFields}, so it needs to be set even if the transport is
   * HTTP/2.
   */
  public static String get(Request request, Proxy.Type proxyType) {
    StringBuilder result = new StringBuilder();
    result.append(request.method());
    result.append(' ');

    if (includeAuthorityInRequestLine(request, proxyType)) {
      result.append(request.url());
    } else {
      result.append(requestPath(request.url()));
    }

    result.append(" HTTP/1.1");
    return result.toString();
  }

  /**
   * 返回true如果这个request行应该包括完整的URL，包括主机名和端口（就像like "GET http://android.com/foo HTTP/1.1"）
   * 否则只放路径(就像 "GET /foo HTTP/1.1")
   * Returns true if the request line should contain the full URL with host and port (like "GET
   * http://android.com/foo HTTP/1.1") or only the path (like "GET /foo HTTP/1.1").
   */
  private static boolean includeAuthorityInRequestLine(Request request, Proxy.Type proxyType) {
    return !request.isHttps() && proxyType == Proxy.Type.HTTP;
  }

  /**
   * 返回request中的path，就像是'/' 在'GET / HTTP/1.1'. 中。据不能为空，甚至可以设置成request的URL
   * Returns the path to request, like the '/' in 'GET / HTTP/1.1'. Never empty, even if the request
   * URL is. Includes the query component if it exists.
   */
  public static String requestPath(HttpUrl url) {
    String path = url.encodedPath();
    String query = url.encodedQuery();
    return query != null ? (path + '?' + query) : path;
  }
}
