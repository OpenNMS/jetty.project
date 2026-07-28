//
//  ========================================================================
//  Copyright (c) 1995-2022 Mort Bay Consulting Pty Ltd and others.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.server;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * <p>Tests that the authority of the request target and the {@code Host} header cannot disagree,
 * so that a request cannot carry two different host identities
 * (see <a href="https://www.rfc-editor.org/rfc/rfc7230#section-5.4">RFC 7230, section 5.4</a>).</p>
 *
 * @see org.eclipse.jetty.http.HttpComplianceSection#MISMATCHED_AUTHORITY
 */
public class MismatchedAuthorityTest
{
    private Server _server;
    private LocalConnector _connector;

    private void start(HttpCompliance compliance) throws Exception
    {
        _server = new Server();
        HttpConnectionFactory http = new HttpConnectionFactory();
        if (compliance != null)
            http.setHttpCompliance(compliance);
        _connector = new LocalConnector(_server, http);
        _server.addConnector(_connector);
        _server.setHandler(new AbstractHandler()
        {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                baseRequest.setHandled(true);
                response.setStatus(HttpServletResponse.SC_OK);
                response.setHeader("X-Server-Name", request.getServerName());
                response.setHeader("X-Server-Port", String.valueOf(request.getServerPort()));
            }
        });
        _server.start();
    }

    @AfterEach
    public void dispose()
    {
        LifeCycle.stop(_server);
    }

    private HttpTester.Response request(String requestLine, String hostHeader) throws Exception
    {
        String rawRequest = requestLine + "\r\n" +
            (hostHeader == null ? "" : "Host: " + hostHeader + "\r\n") +
            "Connection: close\r\n" +
            "\r\n";
        return HttpTester.parseResponse(_connector.getResponse(rawRequest));
    }

    @Test
    public void testMismatchedAuthorityIsRejected() throws Exception
    {
        start(null);
        HttpTester.Response response = request("GET http://myhost:8888/path HTTP/1.1", "otherhost:8888");
        assertThat(response.getStatus(), is(400));
    }

    @Test
    public void testMismatchedPortIsRejected() throws Exception
    {
        start(null);
        HttpTester.Response response = request("GET http://myhost:8888/path HTTP/1.1", "myhost:9999");
        assertThat(response.getStatus(), is(400));
    }

    @Test
    public void testMismatchedAuthorityIsRejectedForCaseInsensitiveHost() throws Exception
    {
        start(null);
        HttpTester.Response response = request("GET http://myhost:8888/path HTTP/1.1", "notmyhost:8888");
        assertThat(response.getStatus(), is(400));
    }

    @Test
    public void testGarbageHostHeaderIsRejected() throws Exception
    {
        start(null);
        HttpTester.Response response = request("GET http://myhost:8888/path HTTP/1.1", "myhost:not_a_port");
        assertThat(response.getStatus(), is(400));
    }

    /**
     * A second {@code Host} header that disagrees with the authority must be rejected too, otherwise
     * an intermediary and Jetty may pick a different one of the two.
     */
    @Test
    public void testSecondMismatchedHostHeaderIsRejected() throws Exception
    {
        start(null);
        String rawRequest = "GET /path HTTP/1.1\r\n" +
            "Host: myhost:8888\r\n" +
            "Host: otherhost:8888\r\n" +
            "Connection: close\r\n" +
            "\r\n";
        HttpTester.Response response = HttpTester.parseResponse(_connector.getResponse(rawRequest));
        assertThat(response.getStatus(), is(400));
    }

    @Test
    public void testMatchingAuthorityIsAccepted() throws Exception
    {
        start(null);
        HttpTester.Response response = request("GET http://myhost:8888/path HTTP/1.1", "myhost:8888");
        assertThat(response.getStatus(), is(200));
        assertEquals("myhost", response.get("X-Server-Name"));
        assertEquals("8888", response.get("X-Server-Port"));
    }

    @Test
    public void testMatchingAuthorityIsAcceptedIgnoringCase() throws Exception
    {
        start(null);
        HttpTester.Response response = request("GET http://MyHost:8888/path HTTP/1.1", "myhost:8888");
        assertThat(response.getStatus(), is(200));
    }

    /**
     * An implied default port on one side and an explicit default port on the other side
     * denote the same authority, so such a request must be accepted.
     */
    @ParameterizedTest
    @ValueSource(strings = {"myhost", "myhost:80"})
    public void testDefaultPortIsEquivalentToNoPort(String hostHeader) throws Exception
    {
        start(null);
        HttpTester.Response response = request("GET http://myhost/path HTTP/1.1", hostHeader);
        assertThat(response.getStatus(), is(200));
        assertEquals("myhost", response.get("X-Server-Name"));

        response = request("GET http://myhost:80/path HTTP/1.1", hostHeader);
        assertThat(response.getStatus(), is(200));
        assertEquals("myhost", response.get("X-Server-Name"));
    }

    @Test
    public void testIPv6AuthorityIsAccepted() throws Exception
    {
        start(null);
        HttpTester.Response response = request("GET http://[::1]:8888/path HTTP/1.1", "[::1]:8888");
        assertThat(response.getStatus(), is(200));
        assertEquals("[::1]", response.get("X-Server-Name"));
    }

    @Test
    public void testMismatchedIPv6AuthorityIsRejected() throws Exception
    {
        start(null);
        HttpTester.Response response = request("GET http://[::1]:8888/path HTTP/1.1", "[::2]:8888");
        assertThat(response.getStatus(), is(400));
    }

    /**
     * Without a {@code Host} header there is only one host identity, so there is nothing to mismatch.
     */
    @Test
    public void testAbsoluteURIWithoutHostHeaderIsAccepted() throws Exception
    {
        start(null);
        HttpTester.Response response = HttpTester.parseResponse(_connector.getResponse(
            "GET http://myhost:8888/path HTTP/1.0\r\n" +
                "\r\n"));
        assertThat(response.getStatus(), is(200));
        assertEquals("myhost", response.get("X-Server-Name"));
    }

    @Test
    public void testOriginFormRequestIsAccepted() throws Exception
    {
        start(null);
        HttpTester.Response response = request("GET /path HTTP/1.1", "myhost:8888");
        assertThat(response.getStatus(), is(200));
        assertEquals("myhost", response.get("X-Server-Name"));
    }

    /**
     * RFC2616 section 5.2 gives precedence to the authority of an absolute request URI, so the
     * RFC2616 compliance mode does not require the authority and the {@code Host} header to match.
     */
    @Test
    public void testMismatchedAuthorityIsAllowedByRFC2616() throws Exception
    {
        start(HttpCompliance.RFC2616);
        HttpTester.Response response = request("GET http://myhost:8888/path HTTP/1.1", "otherhost:9999");
        assertThat(response.getStatus(), is(200));
        assertEquals("myhost", response.get("X-Server-Name"));
        assertEquals("8888", response.get("X-Server-Port"));
    }
}
