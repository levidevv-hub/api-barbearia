package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.dto.StatusForwardingResult;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class StatusForwardingClientService implements IStatusForwardingClient {
    private final HttpClient client;
    private final URI target;

    public StatusForwardingClientService(HttpClient client, URI target) {
        this.client = client;
        this.target = target;
    }

    @Override
    public StatusForwardingResult forward(byte[] body, String signature) {
        try {
            var request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Hub-Signature-256", signature)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return new StatusForwardingResult(response.statusCode() == 200, response.statusCode(),
                    response.statusCode() == 200 ? null : "HTTP_REJECTED");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new StatusForwardingResult(false, null, "INTERRUPTED");
        } catch (IOException | RuntimeException ex) {
            return new StatusForwardingResult(false, null, "CONNECTION_ERROR");
        }
    }
}
