package monero.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages a collection of prioritized Monero RPC connections.
 */
public class MoneroConnectionManager {
  
  // static variables
  private static final long DEFAULT_TIMEOUT = 5000l;
  private static final long DEFAULT_CHECK_CONNECTION_PERIOD = 10000l;
  
  // instance variables
  private MoneroRpcConnection currentConnection;
  private List<MoneroRpcConnection> connections = new ArrayList<MoneroRpcConnection>();
  private List<MoneroConnectionManagerListener> listeners = new ArrayList<MoneroConnectionManagerListener>();
  private ConnectionComparator connectionComparator = new ConnectionComparator();
  private long timeoutMs = DEFAULT_TIMEOUT;
  private boolean autoSwitch;
  private TaskLooper checkConnectionLooper;
  
  /**
   * Add a listener to receive notifications when the connection changes.
   * 
   * @param listener - the listener to add
   * @return this connection manager for chaining
   */
  public MoneroConnectionManager addListener(MoneroConnectionManagerListener listener) {
    listeners.add(listener);
    return this;
  }
  
  /**
   * Remove a listener.
   * 
   * @param listener - the listener to remove
   * @return this connection manager for chaining
   */
  public MoneroConnectionManager removeListener(MoneroConnectionManagerListener listener) {
    if (!listeners.remove(listener)) throw new MoneroError("Monero connection manager does not contain listener to remove");
    return this;
  }
  
  /**
   * Add a connection. The connection may have an elevated priority for this manager to use.
   * 
   * @param connection - the connection to add
   * @return this connection manager for chaining
   */
  public MoneroConnectionManager addConnection(MoneroRpcConnection connection) {
    for (MoneroRpcConnection aConnection : connections) {
      if (aConnection.getUri().equals(connection.getUri())) throw new MoneroError("Connection URI already exists with connection manager: " + connection.getUri());
    }
    connections.add(connection);
    return this;
  }
  
  /**
   * Remove a connection.
   * 
   * @param uri - uri of the connection to remove
   * @return this connection manager for chaining
   */
  public MoneroConnectionManager removeConnection(String uri) {
    MoneroRpcConnection connection = getConnectionByUri(uri);
    if (connection == null) throw new MoneroError("Connection manager does not contain connection to remove: " + uri);
    connections.remove(connection);
    if (connection == currentConnection) currentConnection = null;
    return this;
  }
  
  /**
   * Indicates if the connection manager is connected to a node.
   * 
   * @return true if the current connection is set, online, and not unauthenticated. false otherwise
   */
  public boolean isConnected() {
    return currentConnection != null && Boolean.TRUE.equals(currentConnection.isOnline()) && !Boolean.FALSE.equals(currentConnection.isAuthenticated());
  }
  
  /**
   * Get the current connection.
   * 
   * @return the current connection or null if no connection set
   */
  public MoneroRpcConnection getConnection() {
    return currentConnection;
  }
  
  /**
   * Get a connection by URI.
   * 
   * @param uri is the URI of the connection to get
   * @return the connection with the URI or null if no connection with the URI exists
   */
  public MoneroRpcConnection getConnectionByUri(String uri) {
    for (MoneroRpcConnection connection : connections) if (connection.getUri().equals(uri)) return connection;
    return null;
  }
  
  /**
   * Get all connections in order of current connection (if applicable), online status, priority, and name.
   * 
   * @return the list of sorted connections
   */
  public List<MoneroRpcConnection> getConnections() {
    List<MoneroRpcConnection> sortedConnections = new ArrayList<MoneroRpcConnection>(connections);
    Collections.sort(sortedConnections, connectionComparator);
    return sortedConnections;
  }
  
  /**
   * Get the best available connection in order of priority then response time.
   * 
   * @return the best available connection in order of priority then response time, null if no connections available
   */
  public MoneroRpcConnection getBestAvailableConnection() {
    
    // try connections within each descending priority
    for (List<MoneroRpcConnection> prioritizedConnections : getConnectionsInDescendingPriority()) {
      try {
      
        // check connections in parallel
        ExecutorService pool = Executors.newFixedThreadPool(prioritizedConnections.size());
        CompletionService<MoneroRpcConnection> completionService = new ExecutorCompletionService<MoneroRpcConnection>(pool);
        for (MoneroRpcConnection connection : prioritizedConnections) {
          completionService.submit(new Runnable() {
            @Override
            public void run() {
              connection.checkConnection(timeoutMs);
            }
          }, connection);
        }
        
        // use first available connection
        pool.shutdown();
        for (int i = 0; i < prioritizedConnections.size(); i++) {
          MoneroRpcConnection connection = completionService.take().get();
          if (connection.isOnline() && !Boolean.FALSE.equals(connection.isAuthenticated())) {
            return connection;
          }
        }
      } catch (Exception e) {
        throw new MoneroError(e);
      }
    }
    return null;
  }
  
  /**
   * Set the current connection without changing the credentials.
   * 
   * @param uri identifies the connection to make current
   * @return this connection manager for chaining
   */
  public MoneroConnectionManager setConnection(String uri) {
    if (uri == null) setConnection((MoneroRpcConnection) null);
    else {
      MoneroRpcConnection uriConnection = getConnectionByUri(uri);
      if (currentConnection == uriConnection) return this;
      currentConnection = uriConnection;
      onConnectionChanged(currentConnection);
    }
    return this;
  }
  
  /**
   * Set the current connection.
   * Update credentials if connection's URI was previously added. Otherwise add new connection.
   * Notify if current connection changed.
   * Does not check the connection.
   * 
   * @param connection is the connection to make current
   * @return this connection manager for chaining
   */
  public MoneroConnectionManager setConnection(MoneroRpcConnection connection) {
    if (currentConnection == connection) return this;
    
    // check if setting null connection
    if (connection == null) {
      currentConnection = null;
      onConnectionChanged(null);
      return this;
    }
    
    // check if adding new connection
    MoneroRpcConnection prevConnection = getConnectionByUri(connection.getUri());
    if (prevConnection == null) {
      addConnection(connection);
      currentConnection = connection;
      onConnectionChanged(currentConnection);
      return this;
    }
    
    // check if updating current connection
    if (prevConnection != currentConnection || !Objects.equals(prevConnection.getUsername(), connection.getUsername()) || !Objects.equals(prevConnection.getPassword(), connection.getPassword())) {
      prevConnection.setCredentials(connection.getUsername(), connection.getPassword());
      currentConnection = prevConnection;
      onConnectionChanged(currentConnection);
    }
    
    return this;
  }
  
  /**
   * Check the current connection. If disconected and auto switch enabled, switches to best available connection.
   * 
   * @return this connection manager for chaining
   */
  public MoneroConnectionManager checkConnection() {
    MoneroRpcConnection connection = getConnection();
    if (connection != null && connection.checkConnection(timeoutMs)) onConnectionChanged(connection);
    if (autoSwitch && (connection == null || !connection.isOnline() || Boolean.FALSE.equals(connection.isAuthenticated()))) setConnection(getBestAvailableConnection());
    return this;
  }
  
  /**
   * Check all managed connections.
   * 
   * @return this connection manager for chaining
   */
  public MoneroConnectionManager checkConnections() {
    MoneroRpcConnection currentConnection = getConnection();
    ExecutorService pool = Executors.newFixedThreadPool(connections.size());
    for (MoneroRpcConnection connection : connections) {
      pool.submit(new Runnable() {
        @Override
        public void run() {
          try {
            if (connection.checkConnection(timeoutMs) && connection == currentConnection) onConnectionChanged(connection);
          } catch (MoneroError err) {
            // ignore error
          }
        }
      });
    }
    try {
      pool.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      throw new MoneroError(e);
    }
    return this;
  }
  
  /**
   * Start checking connection status by polling the server in a fixed period loop.
   * 
   * @return this connection manager for chaining
   */
  public MoneroConnectionManager startCheckingConnection() {
    startCheckingConnection(null);
    return this;
  }
  
  /**
   * Start checking connection status by polling the server in a fixed period loop.
   * 
   * @param periodMs is the time between checks in milliseconds (default 10000 ms or 10 seconds)
   * @return this connection manager for chaining
   */
  public synchronized MoneroConnectionManager startCheckingConnection(Long periodMs) {
    if (periodMs == null) periodMs = DEFAULT_CHECK_CONNECTION_PERIOD;
    if (checkConnectionLooper == null) {
      checkConnectionLooper = new TaskLooper(new Runnable() {
        @Override
        public void run() {
          try { if (getConnection() != null) checkConnection(); }
          catch (Exception e) { e.printStackTrace(); }
        }
      });
    }
    checkConnectionLooper.start(periodMs);
    return this;
  }
  
  /**
   * Stop automatically checking the connection status.
   * 
   * @return this connection manager for chaining
   */
  public MoneroConnectionManager stopCheckingConnection() {
    if (checkConnectionLooper != null) checkConnectionLooper.stop();
    return this;
  }
  
  /**
   * Automatically switch to best available connection if current connection is disconnected after being checked.
   * 
   * @param autoSwitch specifies if the connection should switch on disconnect
   * @return this connection manager for chaining
   */
  public MoneroConnectionManager setAutoSwitch(boolean autoSwitch) {
    this.autoSwitch = autoSwitch;
    return this;
  }
  
  /**
   * Set the maximum request time before a connection is considered offline.
   * 
   * @param timeoutInMs is the timeout before a connection is considered offline
   * @return this connection manager for chaining
   */
  public MoneroConnectionManager setTimeout(long timeoutInMs) {
    this.timeoutMs = timeoutInMs;
    return this;
  }
  
  /**
   * Get the request timeout.
   * 
   * @return the request timeout before a connection is considered offline
   */
  public long getTimeout() {
    return timeoutMs;
  }
  
  /**
   * Collect connectable peers of the managed connections.
   *
   * @return connectable peers
   */
  public List<MoneroRpcConnection> getPeerConnections() {
    throw new RuntimeException("Not implemented");
  }
  
  // ------------------------------ PRIVATE HELPERS ---------------------------
  
  private void onConnectionChanged(MoneroRpcConnection connection) {
    for (MoneroConnectionManagerListener listener : listeners) listener.onConnectionChanged(connection);
  }
  
  private List<List<MoneroRpcConnection>> getConnectionsInDescendingPriority() {
    Map<Integer, List<MoneroRpcConnection>> connectionPriorities = new LinkedHashMap<Integer, List<MoneroRpcConnection>>();
    for (MoneroRpcConnection connection : connections) {
      if (!connectionPriorities.containsKey(connection.getPriority())) connectionPriorities.put(connection.getPriority(), new ArrayList<MoneroRpcConnection>());
      connectionPriorities.get(connection.getPriority()).add(connection);
    }
    List<List<MoneroRpcConnection>> prioritizedConnections = new ArrayList<List<MoneroRpcConnection>>();
    for (List<MoneroRpcConnection> priorityConnections : connectionPriorities.values()) prioritizedConnections.add(priorityConnections);
    Collections.reverse(prioritizedConnections);
    return prioritizedConnections;
  }
  
  private class ConnectionComparator implements Comparator<MoneroRpcConnection> {
    
    @Override
    public int compare(MoneroRpcConnection c1, MoneroRpcConnection c2) {
      
      // current connection is first
      if (c1 == currentConnection) return -1;
      if (c2 == currentConnection) return 1;
      
      // order by availability then priority then by name
      if (c1.isOnline() == c2.isOnline()) {
        if (c1.getPriority() == c2.getPriority()) return c1.getUri().compareTo(c2.getUri());
        else return c1.getPriority() > c2.getPriority() ? -1 : 1;
      } else {
        if (Boolean.TRUE.equals(c1.isOnline())) return -1;
        else if (Boolean.TRUE.equals(c2.isOnline())) return 1;
        else if (c1.isOnline() == null) return -1;
        else return 1; // c1 is offline
      }
    }
  }
}
