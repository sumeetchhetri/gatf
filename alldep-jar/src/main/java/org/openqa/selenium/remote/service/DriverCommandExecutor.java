// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.remote.service;

import static org.openqa.selenium.concurrent.ExecutorServices.shutdownGracefully;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.CommandInfo;
import org.openqa.selenium.remote.DriverCommand;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.Response;
import org.openqa.selenium.remote.http.ClientConfig;

/**
 * A specialized {@link HttpCommandExecutor} that will use a {@link DriverService} that lives and
 * dies with a single WebDriver session. The service will be restarted upon each new session request
 * and shutdown after each quit command.
 */
public class DriverCommandExecutor extends HttpCommandExecutor implements Closeable {

  private static final String NAME = "Driver Command Executor";
  private final DriverService service;
  private final ExecutorService executorService =
      Executors.newFixedThreadPool(
          2,
          r -> {
            Thread thread = new Thread(r);
            thread.setName(NAME);
            thread.setDaemon(true);
            return thread;
          });

  /**
   * Creates a new DriverCommandExecutor which will communicate with the driver as configured by the
   * given {@code service}.
   *
   * @param service The DriverService to send commands to.
   */
  public DriverCommandExecutor(DriverService service) {
    this(service, ClientConfig.defaultConfig());
  }

  public DriverCommandExecutor(DriverService service, ClientConfig clientConfig) {
    this(service, Collections.emptyMap(), clientConfig);
  }

  /**
   * Creates an {@link DriverCommandExecutor} that supports non-standard {@code additionalCommands}
   * in addition to the standard.
   *
   * @param service driver server
   * @param additionalCommands additional commands the remote end can process
   * @param clientConfig
   */
  public DriverCommandExecutor(
      DriverService service,
      Map<String, CommandInfo> additionalCommands,
      ClientConfig clientConfig) {
    super(
        additionalCommands,
        service.getUrl(),
        computeClientConfigWithBaseURI(clientConfig, service));
    this.service = service;
  }

  private static ClientConfig computeClientConfigWithBaseURI(
      ClientConfig clientConfig, DriverService service) {
    try {
      return clientConfig.baseUri(service.getUrl().toURI());
    } catch (URISyntaxException e) {
      return clientConfig;
    }
  }

  /**
   * Sends the {@code command} to the driver server for execution. The server will be started if
   * requesting a new session. Likewise, if terminating a session, the server will be shutdown once
   * a response is received.
   *
   * @param command The command to execute.
   * @return The command response.
   * @throws IOException If an I/O error occurs while sending the command.
   */
  @Override
  public Response execute(Command command) throws IOException {
    boolean newlyStarted = false;
    if (DriverCommand.NEW_SESSION.equals(command.getName())) {
      boolean wasRunningBefore = service.isRunning();
      service.start();
      newlyStarted = !wasRunningBefore && service.isRunning();
    }

    org.apache.commons.lang3.tuple.ImmutableTriple<Boolean, String, String[]> IN_DOCKER = com.gatf.selenium.SeleniumTest.IN_DOCKER.get();
    if (DriverCommand.QUIT.equals(command.getName())) {
      CompletableFuture<Response> commandComplete =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  com.gatf.selenium.SeleniumTest.IN_DOCKER.set(IN_DOCKER);
                  return invokeExecute(command);
                } catch (Throwable t) {
                  Throwable rootCause = rootCause(t);
                  if (rootCause instanceof IllegalStateException
                      && "Closed".equals(rootCause.getMessage())) {
                    return null;
                  }
                  if (rootCause instanceof ConnectException
                      && "Connection refused".equals(rootCause.getMessage())) {
                    throw new WebDriverException("The driver server has unexpectedly died!", t);
                  }
                  if (t instanceof Error) throw (Error) t;
                  if (t instanceof RuntimeException) throw (RuntimeException) t;
                  throw new WebDriverException(t);
                } finally {
                  com.gatf.selenium.SeleniumTest.IN_DOCKER.remove();
                }
              },
              executorService);

      CompletableFuture<Response> processFinished =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  service.process.waitFor(service.getTimeout());
                } catch (InterruptedException ex) {
                  Thread.currentThread().interrupt();
                }
                return null;
              },
              executorService);

      try {
        Response response =
            (Response)
                CompletableFuture.anyOf(commandComplete, processFinished)
                    .get(service.getTimeout().toMillis() * 2, TimeUnit.MILLISECONDS);
        service.stop();
        return response;
      } catch (ExecutionException | TimeoutException e) {
        throw new WebDriverException("Timed out waiting for driver server to stop.", e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new WebDriverException("Timed out waiting for driver server to stop.", e);
      } finally {
        close();
      }

    } else {
      try {
        return invokeExecute(command);
      } catch (Throwable t) {
        Throwable rootCause = rootCause(t);
        if (rootCause instanceof ConnectException
            && "Connection refused".equals(rootCause.getMessage())
            && !service.isRunning()) {
          throw new WebDriverException("The driver server has unexpectedly died!", t);
        }
        // an attempt to execute a command in the newly started driver server has failed
        // hence need to stop it
        if (newlyStarted && service.isRunning()) {
          try {
            service.stop();
          } catch (Exception ignored) {
            // fall through
          }
        }
        if (t instanceof Error) throw (Error) t;
        if (t instanceof RuntimeException) throw (RuntimeException) t;
        throw new WebDriverException(t);
      }
    }
  }

  private static Throwable rootCause(Throwable throwable) {
    Throwable cause = throwable;

    for (int i = 0; i < 99; i++) {
      Throwable peek = cause.getCause();

      if (peek != null) {
        cause = peek;
      } else {
        return cause;
      }
    }

    throw new IllegalArgumentException("to many causes or recursive causes");
  }

  /** visible for testing only */
  Response invokeExecute(Command command) throws IOException {
    return super.execute(command);
  }

  @Override
  public void close() {
    shutdownGracefully(NAME, executorService);
  }
}