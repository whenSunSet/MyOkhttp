/*
 * Copyright (C) 2012 Square, Inc.
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import okhttp3.Address;
import okhttp3.HttpUrl;
import okhttp3.Route;
import okhttp3.internal.Util;

/**
 * 选择routes来连接原始服务器。每一个connection 都需要选择一个proxy server，IP address和TLS mode
 * Connections也需要被回收重用
 * Selects routes to connect to an origin server. Each connection requires a choice of proxy server,
 * IP address, and TLS mode. Connections may also be recycled.
 */
public final class RouteSelector {
  private final Address address;
  private final RouteDatabase routeDatabase;

  /*
   最近尝试的route
   The most recently attempted route. */
  private Proxy lastProxy;
  private InetSocketAddress lastInetSocketAddress;

  /*
  负责找到下一个proxy来使用
  State for negotiating the next proxy to use. */
  private List<Proxy> proxies = Collections.emptyList();
  private int nextProxyIndex;

  /*
  负责找到下一个socket address 来使用
  State for negotiating the next socket address to use. */
  private List<InetSocketAddress> inetSocketAddresses = Collections.emptyList();
  private int nextInetSocketAddressIndex;

  /*
  负责找到错误的routes
  State for negotiating failed routes */
  private final List<Route> postponedRoutes = new ArrayList<>();

  public RouteSelector(Address address, RouteDatabase routeDatabase) {
    this.address = address;
    this.routeDatabase = routeDatabase;

    resetNextProxy(address.url(), address.proxy());
  }

  /**
   * 返回true，如果这里有另外的route可以被使用。每一个address至少有一个route
   * Returns true if there's another route to attempt. Every address has at least one route.
   */
  public boolean hasNext() {
    return hasNextInetSocketAddress()
        || hasNextProxy()
        || hasNextPostponed();
  }

  public Route next() throws IOException {
    // 计算下一个route来使用
    // Compute the next route to attempt.
    if (!hasNextInetSocketAddress()) {
      if (!hasNextProxy()) {
        if (!hasNextPostponed()) {
          throw new NoSuchElementException();
        }
        return nextPostponed();
      }
      lastProxy = nextProxy();
    }
    lastInetSocketAddress = nextInetSocketAddress();

    Route route = new Route(address, lastProxy, lastInetSocketAddress);
    if (routeDatabase.shouldPostpone(route)) {
      postponedRoutes.add(route);
      // 我们只会递归为了跳过之前失败的路线。他们将尝试最后一次。
      // We will only recurse in order to skip previously failed routes. They will be tried last.
      return next();
    }

    return route;
  }

  /**
   * 客户端需要调用这个方法当他们遇到了一个失败的连接，这里连接是从这里选择的
   * Clients should invoke this method when they encounter a connectivity failure on a connection
   * returned by this route selector.
   */
  public void connectFailed(Route failedRoute, IOException failure) {
    if (failedRoute.proxy().type() != Proxy.Type.DIRECT && address.proxySelector() != null) {
      // 告诉proxy selector当我们与一个新鲜的connection连接失败
      // Tell the proxy selector when we fail to connect on a fresh connection.
      address.proxySelector().connectFailed(
          address.url().uri(), failedRoute.proxy().address(), failure);
    }

    routeDatabase.failed(failedRoute);
  }

  /**
   * 准备proxy servers 来重试
   * Prepares the proxy servers to try. */
  private void resetNextProxy(HttpUrl url, Proxy proxy) {
    if (proxy != null) {
      // 如果用户指定了一个proxy，那么就只尝试这个
      // If the user specifies a proxy, try that and only that.
      proxies = Collections.singletonList(proxy);
    } else {
      // 尝试所有的ProxySelector，直至连接成功
      // Try each of the ProxySelector choices until one connection succeeds.
      List<Proxy> proxiesOrNull = address.proxySelector().select(url.uri());
      proxies = proxiesOrNull != null && !proxiesOrNull.isEmpty()
          ? Util.immutableList(proxiesOrNull)
          : Util.immutableList(Proxy.NO_PROXY);
    }
    nextProxyIndex = 0;
  }

  /**
   * 返回true，如果这里有其他的proxy可以被尝试
   * Returns true if there's another proxy to try. */
  private boolean hasNextProxy() {
    return nextProxyIndex < proxies.size();
  }

  /**
   * 返回下一个proxy来尝试，可能回事PROXY.NO_PROXY，但是绝不会是null
   * Returns the next proxy to try. May be PROXY.NO_PROXY but never null. */
  private Proxy nextProxy() throws IOException {
    if (!hasNextProxy()) {
      throw new SocketException("No route to " + address.url().host()
          + "; exhausted proxy configurations: " + proxies);
    }
    Proxy result = proxies.get(nextProxyIndex++);
    resetNextInetSocketAddress(result);
    return result;
  }

  /**
   * 准备socket addresses 来尝试一个当前的proxy或者主机
   * Prepares the socket addresses to attempt for the current proxy or host. */
  private void resetNextInetSocketAddress(Proxy proxy) throws IOException {
    // Clear the addresses. Necessary if getAllByName() below throws!
    inetSocketAddresses = new ArrayList<>();

    String socketHost;
    int socketPort;
    if (proxy.type() == Proxy.Type.DIRECT || proxy.type() == Proxy.Type.SOCKS) {
      socketHost = address.url().host();
      socketPort = address.url().port();
    } else {
      SocketAddress proxyAddress = proxy.address();
      if (!(proxyAddress instanceof InetSocketAddress)) {
        throw new IllegalArgumentException(
            "Proxy.address() is not an " + "InetSocketAddress: " + proxyAddress.getClass());
      }
      InetSocketAddress proxySocketAddress = (InetSocketAddress) proxyAddress;
      socketHost = getHostString(proxySocketAddress);
      socketPort = proxySocketAddress.getPort();
    }

    if (socketPort < 1 || socketPort > 65535) {
      throw new SocketException("No route to " + socketHost + ":" + socketPort
          + "; port is out of range");
    }

    if (proxy.type() == Proxy.Type.SOCKS) {
      inetSocketAddresses.add(InetSocketAddress.createUnresolved(socketHost, socketPort));
    } else {
      // 在IPv4/IPv6 混合的环境下，用最好的行为尝试每一个地址。
      // Try each address for best behavior in mixed IPv4/IPv6 environments.
      List<InetAddress> addresses = address.dns().lookup(socketHost);
      for (int i = 0, size = addresses.size(); i < size; i++) {
        InetAddress inetAddress = addresses.get(i);
        inetSocketAddresses.add(new InetSocketAddress(inetAddress, socketPort));
      }
    }

    nextInetSocketAddressIndex = 0;
  }

  /**
   * 从一个{@link InetSocketAddress}中获得一个"host"。这里返回一个包括了真正的主机名或者IP address的string
   * Obtain a "host" from an {@link InetSocketAddress}. This returns a string containing either an
   * actual host name or a numeric IP address.
   */
  static String getHostString(InetSocketAddress socketAddress) {
    InetAddress address = socketAddress.getAddress();
    if (address == null) {
      // InetSocketAddress是一个特别的string (不是一个IP 就是一个 host name)，如果是一个host name
      // 那么所有的对这个host name的ip应该被尝试，如果只是一个IP，那么只有这个ip地址需要被尝试
      // The InetSocketAddress was specified with a string (either a numeric IP or a host name). If
      // it is a name, all IPs for that name should be tried. If it is an IP address, only that IP
      // address should be tried.
      return socketAddress.getHostName();
    }
    // 这个InetSocketAddress有一个特别的地址：我们只需要尝试这个地址就可以。
    // 那么我们就返回这个地址并且忽略任何host name
    // The InetSocketAddress has a specific address: we should only try that address. Therefore we
    // return the address and ignore any host name that may be available.
    return address.getHostAddress();
  }

  /**
   * 返回true，如果这里没有其他的socket address可以被尝试
   * Returns true if there's another socket address to try. */
  private boolean hasNextInetSocketAddress() {
    return nextInetSocketAddressIndex < inetSocketAddresses.size();
  }

  /**
   * 返回下一个socket address 来尝试
   * Returns the next socket address to try. */
  private InetSocketAddress nextInetSocketAddress() throws IOException {
    if (!hasNextInetSocketAddress()) {
      throw new SocketException("No route to " + address.url().host()
          + "; exhausted inet socket addresses: " + inetSocketAddresses);
    }
    return inetSocketAddresses.get(nextInetSocketAddressIndex++);
  }

  /**
   * 返回true如果这里有其他的postponed route可以被尝试
   * Returns true if there is another postponed route to try. */
  private boolean hasNextPostponed() {
    return !postponedRoutes.isEmpty();
  }

  /**
   * 返回一下一个postponed route 来尝试
   * Returns the next postponed route to try. */
  private Route nextPostponed() {
    return postponedRoutes.remove(0);
  }
}
