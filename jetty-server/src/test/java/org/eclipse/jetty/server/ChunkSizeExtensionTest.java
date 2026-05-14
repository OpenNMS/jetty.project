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
import java.io.InputStream;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

public class ChunkSizeExtensionTest
{
    private Server _server;
    private LocalConnector _connector;

    @BeforeEach
    public void init() throws Exception
    {
        _server = new Server();

        HttpConfiguration config = new HttpConfiguration();
        config.setRequestHeaderSize(1024);
        config.setResponseHeaderSize(1024);
        config.setSendDateHeader(true);
        HttpConnectionFactory http = new HttpConnectionFactory(config);

        _connector = new LocalConnector(_server, http, null);
        _connector.setIdleTimeout(5000);
        _server.addConnector(_connector);
        _server.setHandler(new DumpHandler());
        ErrorHandler eh = new ErrorHandler();
        eh.setServer(_server);
        _server.addBean(eh);
        _server.start();
    }

    @AfterEach
    public void destroy() throws Exception
    {
        _server.stop();
        _server.join();
    }

    private void consumeRequest(InputStream in) throws IOException
    {
        byte[] buffer = new byte[1024];
        while (in.read(buffer) != -1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testExtensionNoValue(boolean bws) throws Exception
    {
        String ws = bws ? "\t" : "";

        String request = String.format(
            "POST / HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n" +
            "10%sW;%sWext\r\n" +
            "0123456789ABCDEF\r\n" +
            "0\r\n" +
            "\r\n",
            ws, ws
        );

        String rawResponse = _connector.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testExtensionWithValue(boolean bws) throws Exception
    {
        String ws = bws ? "\t" : "";

        String request = String.format(
            "POST / HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n" +
            "10%sW;%sWext\r\n" +
            "0123456789ABCDEF\r\n" +
            "0\r\n" +
            "\r\n",
            ws, ws
        );

        String rawResponse = _connector.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testExtensionWithQuotedValue(boolean bws) throws Exception
    {
        String ws = bws ? "\t" : "";

        String request = String.format(
            "POST / HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n" +
            "1%sW;%sWext%s=%s\"val\"\r\n" +
            "X\r\n" +
            "0\r\n" +
            "\r\n",
            ws, ws, ws, ws
        );

        String rawResponse = _connector.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testMultipleExtensionsNoValues(boolean bws) throws Exception
    {
        String ws = bws ? "\t" : "";

        String request = String.format(
            "POST / HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n" +
            "4%sW;%sWext1%s;%sWext2\r\n" +
            "0123\r\n" +
            "0\r\n" +
            "\r\n",
            ws, ws, ws, ws
        );

        String rawResponse = _connector.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testMultipleExtensionsWithValues(boolean bws) throws Exception
    {
        String ws = bws ? "\t" : "";

        String request = String.format(
            "POST / HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n" +
            "4%sW;%sWext1%s=%s1%s;%sWext2%s=%s2\r\n" +
            "0123\r\n" +
            "0\r\n" +
            "\r\n",
            ws, ws, ws, ws, ws, ws, ws, ws
        );

        String rawResponse = _connector.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testMultipleExtensionsWithQuotes(boolean bws) throws Exception
    {
        String ws = bws ? "\t" : "";

        String request = String.format(
            "POST / HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n" +
            "4%sW;%sWext1%s=%s\"1\"%s;%sWext2%s=%s\"2\"\r\n" +
            "0123\r\n" +
            "0\r\n" +
            "\r\n",
            ws, ws, ws, ws, ws, ws, ws, ws
        );

        String rawResponse = _connector.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    }

    @Test
    public void testEmptyExtension() throws Exception
    {
        String request =
            "POST / HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n" +
            "a;\r\n" +
            "0123456789\r\n" +
            "0\r\n" +
            "\r\n";

        String rawResponse = _connector.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
        assertThat(rawResponse, containsString("Connection: close"));
        assertThat(rawResponse, containsString("Early EOF"));

    }

    @Test
    public void testQuotesWithinQuotes() throws Exception
    {
        String request =
            "POST / HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n" +
            "1;a=\"\\\"val\\\"\"\r\n" +
            "X\r\n" +
            "0\r\n" +
            "\r\n";

        String rawResponse = _connector.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testTerminalChunkWithExtension(boolean quoted) throws Exception
    {
        String q = quoted ? "\"" : "";

        String request = String.format(
            "POST / HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n" +
            "a\r\n" +
            "0123456789\r\n" +
            "0;ext=%sQ1%s\r\n" +
            "\r\n",
            q, q
        );

        String rawResponse = _connector.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    }

    @Test
    public void testTerminalChunkWithExtensionNoValue() throws Exception
    {
        String request =
            "POST / HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n" +
            "a\r\n" +
            "0123456789\r\n" +
            "0;ext\r\n" +
            "\r\n";

        String rawResponse = _connector.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    }

    @Test
    public void testTerminalChunkWithExtensionWithTrailers() throws Exception
    {
        String request =
            "POST / HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n" +
            "a\r\n" +
            "0123456789\r\n" +
            "0;ext\r\n" +
            "Trailer: value\r\n" +
            "\r\n";

        String rawResponse = _connector.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    }
}
