/*
 * Copyright (C) 2015 Square, Inc.
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
package okhttp3.internal.connection;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ProtocolException;
import java.net.UnknownServiceException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLSocket;
import okhttp3.ConnectionSpec;
import okhttp3.internal.Internal;

/**
 * 控制 connection spec 的回滚策略：当一个安全的 socket connection 因为一个 握手或者协议的问题而失败了
 * 这个connection可能会使用不同的协议重试。这个实体是有状态的并且需要被创建使用于单一的connection
 * Handles the connection spec fallback strategy: When a secure socket connection fails due to a
 * handshake / protocol problem the connection may be retried with different protocols. Instances
 * are stateful and should be created and used for a single connection attempt.
 */
public final class ConnectionSpecSelector {

  private final List<ConnectionSpec> connectionSpecs;
  private int nextModeIndex;
  private boolean isFallbackPossible;
  private boolean isFallback;

  public ConnectionSpecSelector(List<ConnectionSpec> connectionSpecs) {
    this.nextModeIndex = 0;
    this.connectionSpecs = connectionSpecs;
  }

  /**
   * 返回{@link SSLSocket}支持的{@link ConnectionSpec}，绝不能返回null。
   * Configures the supplied {@link SSLSocket} to connect to the specified host using an appropriate
   * {@link ConnectionSpec}. Returns the chosen {@link ConnectionSpec}, never {@code null}.
   *
   * @throws IOException 如果这里的socket 不支持任何TLS
   * @throws IOException if the socket does not support any of the TLS modes available
   */
  public ConnectionSpec configureSecureSocket(SSLSocket sslSocket) throws IOException {
    ConnectionSpec tlsConfiguration = null;
    for (int i = nextModeIndex, size = connectionSpecs.size(); i < size; i++) {
      ConnectionSpec connectionSpec = connectionSpecs.get(i);
      if (connectionSpec.isCompatible(sslSocket)) {
        tlsConfiguration = connectionSpec;
        nextModeIndex = i + 1;
        break;
      }
    }

    if (tlsConfiguration == null) {
      // This may be the first time a connection has been attempted and the socket does not support
      // any the required protocols, or it may be a retry (but this socket supports fewer
      // protocols than was suggested by a prior socket).
      throw new UnknownServiceException(
          "Unable to find acceptable protocols. isFallback=" + isFallback
              + ", modes=" + connectionSpecs
              + ", supported protocols=" + Arrays.toString(sslSocket.getEnabledProtocols()));
    }

    isFallbackPossible = isFallbackPossible(sslSocket);

    Internal.instance.apply(tlsConfiguration, sslSocket, isFallback);

    return tlsConfiguration;
  }

  /**
   * 报告一个完成了一个connection测错误。使用下一个{@link ConnectionSpec}来继续尝试
   * Reports a failure to complete a connection. Determines the next {@link ConnectionSpec} to try,
   * if any.
   *
   * 返回true，如果这里的connection应该使用{@link #configureSecureSocket(SSLSocket)}来重试，否则返回false
   * @return {@code true} if the connection should be retried using {@link
   * #configureSecureSocket(SSLSocket)} or {@code false} if not
   */
  public boolean connectionFailed(IOException e) {
    // 表示将来的任何连接尝试这个策略都会退回
    // Any future attempt to connect using this strategy will be a fallback attempt.
    isFallback = true;

    if (!isFallbackPossible) {
      return false;
    }

    // 如果这里有一个协议问题，就不恢复
    // If there was a protocol problem, don't recover.
    if (e instanceof ProtocolException) {
      return false;
    }

    // 如果这里有一个中断或者超时(SocketTimeoutException)，不恢复
    // 当socket超时的时候，我们不会尝试使用不同的连接规范去连接相同的主机，
    // 因为此时我们认为和主机之间是断路
    // If there was an interruption or timeout (SocketTimeoutException), don't recover.
    // For the socket connect timeout case we do not try the same host with a different
    // ConnectionSpec: we assume it is unreachable.
    if (e instanceof InterruptedIOException) {
      return false;
    }

    //
    // Look for known client-side or negotiation errors that are unlikely to be fixed by trying
    // again with a different connection spec.
    if (e instanceof SSLHandshakeException) {
      // 如果这里是从X509TrustManager中来的CertificateException，那么就部重试
      // If the problem was a CertificateException from the X509TrustManager,
      // do not retry.
      if (e.getCause() instanceof CertificateException) {
        return false;
      }
    }
    if (e instanceof SSLPeerUnverifiedException) {
      // e.g. a certificate pinning error.
      return false;
    }

    // 在Android中，SSLProtocolExceptions 可以被导致TLS_FALLBACK_SCSV 错误，这里表示我们可能部应该重试但是我们重试了
    // On Android, SSLProtocolExceptions can be caused by TLS_FALLBACK_SCSV failures, which means we
    // retry those when we probably should not.
    return (e instanceof SSLHandshakeException || e instanceof SSLProtocolException);
  }

  /**
   * 返回true，如果任何之后的{@link ConnectionSpec}在回调策略中看起来是基于{@link SSLSocket}的。
   * 这里假设在未来的socket都会有和目前所支持的socket有相同的能力
   * Returns {@code true} if any later {@link ConnectionSpec} in the fallback strategy looks
   * possible based on the supplied {@link SSLSocket}. It assumes that a future socket will have the
   * same capabilities as the supplied socket.
   */
  private boolean isFallbackPossible(SSLSocket socket) {
    for (int i = nextModeIndex; i < connectionSpecs.size(); i++) {
      if (connectionSpecs.get(i).isCompatible(socket)) {
        return true;
      }
    }
    return false;
  }
}
