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

package org.eclipse.jetty.http2.client;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.util.Promise;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * <p>HTTP/2 conveys the authority of the request target in the {@code :authority} pseudo header, but a
 * request may also carry a {@code Host} header.  If the two are allowed to disagree, then the same request
 * carries two different host identities through the server, which can be used to defeat host based
 * security decisions such as virtual host isolation or host based access control.</p>
 * <p><a href="https://www.rfc-editor.org/rfc/rfc9113#section-8.3.1">RFC 9113, section 8.3.1</a> therefore
 * requires such requests to be treated as malformed.</p>
 *
 * @see org.eclipse.jetty.http.HttpComplianceSection#MISMATCHED_AUTHORITY
 */
public class MismatchedAuthorityTest extends AbstractTest
{
    private void start() throws Exception
    {
        start(new HttpServlet()
        {
            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
            {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setHeader("X-Server-Name", request.getServerName());
                response.setHeader("X-Server-Port", String.valueOf(request.getServerPort()));
            }
        });
    }

    private MetaData.Response request(HttpFields fields) throws Exception
    {
        Session session = newClient(new Session.Listener.Adapter());
        HeadersFrame frame = new HeadersFrame(newRequest("GET", fields), null, true);
        CompletableFuture<MetaData.Response> completable = new CompletableFuture<>();
        session.newStream(frame, new Promise.Adapter<>(), new Stream.Listener.Adapter()
        {
            @Override
            public void onHeaders(Stream stream, HeadersFrame frame)
            {
                MetaData metaData = frame.getMetaData();
                if (metaData.isResponse())
                    completable.complete((MetaData.Response)metaData);
            }
        });
        return completable.get(5, TimeUnit.SECONDS);
    }

    private String authority()
    {
        return "localhost:" + connector.getLocalPort();
    }

    @Test
    public void testMismatchedHostHeaderIsRejected() throws Exception
    {
        start();
        HttpFields fields = new HttpFields();
        fields.put(HttpHeader.HOST, "evil.example.com");
        assertThat(request(fields).getStatus(), is(400));
    }

    @Test
    public void testMismatchedHostHeaderPortIsRejected() throws Exception
    {
        start();
        HttpFields fields = new HttpFields();
        fields.put(HttpHeader.HOST, "localhost:" + (connector.getLocalPort() + 1));
        assertThat(request(fields).getStatus(), is(400));
    }

    /**
     * The {@code Host} header must be checked for every occurrence, so that a smuggled second
     * {@code Host} header cannot be hidden behind a first one that matches the authority.
     */
    @Test
    public void testSecondMismatchedHostHeaderIsRejected() throws Exception
    {
        start();
        HttpFields fields = new HttpFields();
        fields.add(HttpHeader.HOST, authority());
        fields.add(HttpHeader.HOST, "evil.example.com");
        assertThat(request(fields).getStatus(), is(400));
    }

    @Test
    public void testGarbageHostHeaderIsRejected() throws Exception
    {
        start();
        HttpFields fields = new HttpFields();
        fields.put(HttpHeader.HOST, "localhost:not_a_port");
        assertThat(request(fields).getStatus(), is(400));
    }

    @Test
    public void testMatchingHostHeaderIsAccepted() throws Exception
    {
        start();
        HttpFields fields = new HttpFields();
        fields.put(HttpHeader.HOST, authority());
        MetaData.Response response = request(fields);
        assertThat(response.getStatus(), is(200));
        assertThat(response.getFields().get("X-Server-Name"), is("localhost"));
        assertThat(response.getFields().get("X-Server-Port"), is(String.valueOf(connector.getLocalPort())));
    }

    @Test
    public void testMatchingHostHeaderIsAcceptedIgnoringCase() throws Exception
    {
        start();
        HttpFields fields = new HttpFields();
        fields.put(HttpHeader.HOST, "LOCALHOST:" + connector.getLocalPort());
        assertThat(request(fields).getStatus(), is(200));
    }

    /**
     * A request with only the {@code :authority} pseudo header has a single host identity,
     * so there is nothing to mismatch.
     */
    @Test
    public void testNoHostHeaderIsAccepted() throws Exception
    {
        start();
        MetaData.Response response = request(new HttpFields());
        assertThat(response.getStatus(), is(200));
        assertThat(response.getFields().get("X-Server-Name"), is("localhost"));
    }

    /**
     * A deployment that must accept mismatched host identities can select a compliance mode that
     * does not require {@link org.eclipse.jetty.http.HttpComplianceSection#MISMATCHED_AUTHORITY}.
     */
    @Test
    public void testMismatchedHostHeaderIsAllowedByRFC2616() throws Exception
    {
        start();
        connector.addBean(HttpCompliance.RFC2616);
        HttpFields fields = new HttpFields();
        fields.put(HttpHeader.HOST, "evil.example.com");
        MetaData.Response response = request(fields);
        assertThat(response.getStatus(), is(200));
        // The authority of the request target still wins over the Host header.
        assertThat(response.getFields().get("X-Server-Name"), is("localhost"));
    }
}
