package com.yahoo.vespa.hosted.node.admin.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.applicationmodel.HostName;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Retries request on config server a few times before giving up.
 *
 * @author dybdahl
 */
public class ConfigServerHttpRequestExecutor {
    private static final PrefixLogger NODE_ADMIN_LOGGER = PrefixLogger.getNodeAdminLogger(ConfigServerHttpRequestExecutor.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClientBuilder.create().build();
    private final Set<HostName> configServerHosts;
    private final static int MAX_LOOPS = 2;

    public ConfigServerHttpRequestExecutor(Set<HostName> configServerHosts) {
        this.configServerHosts = configServerHosts;
    }

    public interface CreateRequest {
        HttpUriRequest createRequest(HostName configserver) throws JsonProcessingException, UnsupportedEncodingException;
    }

    // return value null means "not found" on server.
    public <T extends Object> T tryAllConfigServers(CreateRequest requestFactory, Class<T> wantedReturnType) {
        Exception lastException = null;
        for (int loopRetry = 0; loopRetry < MAX_LOOPS; loopRetry++) {
            for (HostName configServer : configServerHosts) {
                final HttpResponse response;
                try {
                    response = client.execute(requestFactory.createRequest(configServer));
                } catch (Exception e) {
                    lastException = e;
                    NODE_ADMIN_LOGGER.info("Exception while talking to " + configServer + "(will try all config servers)", e);
                    continue;
                }
                if (response.getStatusLine().getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
                    return null;
                }
                if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
                    NODE_ADMIN_LOGGER.info("Non 200 received:\n" + read(response.getEntity()));
                    throw new RuntimeException("Did not get 200, but " + response.getStatusLine().getStatusCode());
                }
                try {
                    return mapper.readValue(response.getEntity().getContent(), wantedReturnType);
                } catch (IOException e) {
                    throw new RuntimeException("Response didn't contain nodes element, failed parsing?", e);
                }
            }
        }
        throw new RuntimeException("Failed executing request, last exception:", lastException);
    }

    // return value null means "not found" on a config server.
    public <T extends Object> T put(String path, int port, Optional<Object> bodyJsonPojo, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer -> {
            HttpPut put = new HttpPut("http://" + configServer + ":" + port + path);
            if (bodyJsonPojo.isPresent()) {
                put.setEntity(new StringEntity(mapper.writeValueAsString(bodyJsonPojo.get())));
            }
            return put;
        }, wantedReturnType);
    }

    // return value null means "not found" on a config server.
    public <T extends Object> T patch(String path, int port, Object bodyJsonPojo, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer -> {
            HttpPatch patch = new HttpPatch("http://" + configServer + ":" + port + path);
            patch.setEntity(new StringEntity(mapper.writeValueAsString(bodyJsonPojo)));
            return patch;
        }, wantedReturnType);
    }

    // return value null means "not found" on a config server.
    public <T extends Object> T delete(String path, int port, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer -> {
            return new HttpDelete("http://" + configServer + ":" + port + path);
        }, wantedReturnType);
    }

    // return value null means "not found" on a config server.
    public <T extends Object> T get(String path, int port, Class<T> wantedReturnType) {
        return tryAllConfigServers(configServer -> {
            return new HttpGet("http://" + configServer + ":" + port + path);
        }, wantedReturnType);
    }

    private  static String read(HttpEntity input)  {
        try {
            try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input.getContent()))) {
                return buffer.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            return "Failed reading stream: " + e.getMessage();
        }
    }
}