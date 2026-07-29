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

package org.eclipse.jetty.client.util;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.AuthenticationStore;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.TypeUtil;

/**
 * Implementation of the HTTP "Digest" authentication defined in RFC 2617.
 * <p>
 * Applications should create objects of this class and add them to the
 * {@link AuthenticationStore} retrieved from the {@link HttpClient}
 * via {@link HttpClient#getAuthenticationStore()}.
 */
public class DigestAuthentication extends AbstractAuthentication
{
    private final Random random;
    private final String user;
    private final String password;

    /** Construct a DigestAuthentication with a {@link SecureRandom} nonce.
     * @param uri the URI to match for the authentication
     * @param realm the realm to match for the authentication
     * @param user the user that wants to authenticate
     * @param password the password of the user
     */
    public DigestAuthentication(URI uri, String realm, String user, String password)
    {
        this(uri, realm, user, password, new SecureRandom());
    }

    /**
     * @param uri the URI to match for the authentication
     * @param realm the realm to match for the authentication
     * @param user the user that wants to authenticate
     * @param password the password of the user
     * @param random the Random generator to use for nonces.
     */
    public DigestAuthentication(URI uri, String realm, String user, String password, Random random)
    {
        super(uri, realm);
        Objects.requireNonNull(random);
        this.random = random;
        this.user = user;
        this.password = password;
    }

    @Override
    public String getType()
    {
        return "Digest";
    }

    @Override
    public boolean matches(String type, URI uri, String realm)
    {
        // digest authenication requires a realm
        if (realm == null)
            return false;

        return super.matches(type, uri, realm);
    }

    @Override
    public Result authenticate(Request request, ContentResponse response, HeaderInfo headerInfo, Attributes context)
    {
        Map<String, String> params = headerInfo.getParameters();
        String nonce = params.get("nonce");
        if (nonce == null || nonce.length() == 0)
            return null;
        String opaque = params.get("opaque");
        String algorithm = params.get("algorithm");
        if (algorithm == null)
            algorithm = "MD5";
        MessageDigest digester = getMessageDigest(algorithm);
        if (digester == null)
            return null;
        String serverQOP = params.get("qop");
        String clientQOP = null;
        if (serverQOP != null)
        {
            List<String> serverQOPValues = StringUtil.csvSplit(null, serverQOP, 0, serverQOP.length());
            if (serverQOPValues.contains("auth"))
                clientQOP = "auth";
            else if (serverQOPValues.contains("auth-int"))
                clientQOP = "auth-int";
        }

        // RFC 7616[3.3]: the only allowed value for the charset parameter is "UTF-8".
        // Servers that do not send the charset parameter (RFC 2617) imply ISO-8859-1.
        String charsetName = params.get("charset");
        Charset charset = "UTF-8".equalsIgnoreCase(charsetName) ? StandardCharsets.UTF_8 : null;

        String realm = getRealm();
        if (ANY_REALM.equals(realm))
            realm = headerInfo.getRealm();
        return new DigestResult(headerInfo.getHeader(), response.getContent(), realm, user, password, algorithm, nonce, clientQOP, opaque, charset);
    }

    private MessageDigest getMessageDigest(String algorithm)
    {
        try
        {
            return MessageDigest.getInstance(algorithm);
        }
        catch (NoSuchAlgorithmException x)
        {
            return null;
        }
    }

    private class DigestResult implements Result
    {
        private final AtomicInteger nonceCount = new AtomicInteger();
        private final HttpHeader header;
        private final byte[] content;
        private final String realm;
        private final String user;
        private final String password;
        private final String algorithm;
        private final String nonce;
        private final String qop;
        private final String opaque;
        private final Charset charset;

        public DigestResult(HttpHeader header, byte[] content, String realm, String user, String password, String algorithm, String nonce, String qop, String opaque)
        {
            this(header, content, realm, user, password, algorithm, nonce, qop, opaque, null);
        }

        private DigestResult(HttpHeader header, byte[] content, String realm, String user, String password, String algorithm, String nonce, String qop, String opaque, Charset charset)
        {
            this.header = header;
            this.content = content;
            this.realm = realm;
            this.user = user;
            this.password = password;
            this.algorithm = algorithm;
            this.nonce = nonce;
            this.qop = qop;
            this.opaque = opaque;
            this.charset = charset;
        }

        @Override
        public URI getURI()
        {
            return DigestAuthentication.this.getURI();
        }

        @Override
        public void apply(Request request)
        {
            MessageDigest digester = getMessageDigest(algorithm);
            if (digester == null)
                return;

            // Retain ISO-8859-1 for RFC 2617 servers that do not send the charset parameter.
            Charset cs = (charset == null) ? StandardCharsets.ISO_8859_1 : charset;

            String a1 = user + ":" + realm + ":" + password;
            String hashA1 = toHexString(digester.digest(strictEncode(cs, a1)));

            String query = request.getQuery();
            String path = request.getPath();
            String uri = (query == null) ? path : path + "?" + query;
            String a2 = request.getMethod() + ":" + uri;
            if ("auth-int".equals(qop))
                a2 += ":" + toHexString(digester.digest(content));
            String hashA2 = toHexString(digester.digest(strictEncode(cs, a2)));

            String nonceCount;
            String clientNonce;
            String a3;
            if (qop != null)
            {
                nonceCount = nextNonceCount();
                clientNonce = newClientNonce();
                a3 = hashA1 + ":" + nonce + ":" + nonceCount + ":" + clientNonce + ":" + qop + ":" + hashA2;
            }
            else
            {
                nonceCount = null;
                clientNonce = null;
                a3 = hashA1 + ":" + nonce + ":" + hashA2;
            }
            String hashA3 = toHexString(digester.digest(strictEncode(cs, a3)));

            StringBuilder value = new StringBuilder("Digest");
            if (userNameNeedsEncoding(user))
            {
                // RFC 7616[4]: usernames that are not valid in a quoted-string must be
                // sent with the username* parameter, which requires charset=UTF-8.
                if (charset == null)
                    throw new IllegalArgumentException("Unsupported username: " + user);
                value.append(" username*=").append(encodeUserName(user, charset));
            }
            else
            {
                value.append(" username=\"").append(user).append("\"");
            }
            value.append(", realm=\"").append(realm).append("\"");
            value.append(", nonce=\"").append(nonce).append("\"");
            if (opaque != null)
                value.append(", opaque=\"").append(opaque).append("\"");
            value.append(", algorithm=\"").append(algorithm).append("\"");
            value.append(", uri=\"").append(uri).append("\"");
            if (qop != null)
            {
                value.append(", qop=\"").append(qop).append("\"");
                value.append(", nc=\"").append(nonceCount).append("\"");
                value.append(", cnonce=\"").append(clientNonce).append("\"");
            }
            value.append(", response=\"").append(hashA3).append("\"");

            request.header(header, value.toString());
        }

        /**
         * <p>Encodes the given value with the given {@link Charset}, failing if any
         * character cannot be represented in that {@link Charset}.</p>
         * <p>{@link String#getBytes(Charset)} silently replaces unmappable characters
         * with {@code '?'} (byte {@code 0x3F}), so that a two character password made
         * of characters above U+00FF and the password {@code "??"} produce the same
         * ISO-8859-1 bytes, and therefore the same digest, allowing an attacker that
         * knows the username to authenticate with a colliding password.
         * See GHSA-2fvj-hgj9-j2gr.</p>
         *
         * @param charset the {@link Charset} to encode with
         * @param value the value to encode
         * @return the encoded bytes
         * @throws IllegalArgumentException if the value cannot be encoded without loss
         */
        private byte[] strictEncode(Charset charset, String value)
        {
            try
            {
                ByteBuffer byteBuffer = charset.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
                byte[] bytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes);
                return bytes;
            }
            catch (CharacterCodingException x)
            {
                throw new IllegalArgumentException("Could not encode digest parameters with charset " + charset.name(), x);
            }
        }

        private boolean userNameNeedsEncoding(String user)
        {
            // Should be RFC 7230 quoted-string, but use here a simplified version.
            for (int i = 0; i < user.length(); ++i)
            {
                char c = user.charAt(i);
                if (c < 0x20 || c > 0x7E || c == '"' || c == '\\')
                    return true;
            }
            return false;
        }

        private String encodeUserName(String user, Charset charset)
        {
            // RFC 5987 extended value: charset "'" [ language ] "'" value-chars.
            byte[] bytes = strictEncode(charset, user);
            StringBuilder builder = new StringBuilder(charset.name()).append("''");
            for (byte b : bytes)
            {
                int c = b & 0xFF;
                boolean unreserved = (c >= 'A' && c <= 'Z') ||
                    (c >= 'a' && c <= 'z') ||
                    (c >= '0' && c <= '9') ||
                    c == '-' || c == '.' || c == '_' || c == '~';
                if (unreserved)
                    builder.append((char)c);
                else
                    builder.append(String.format("%%%02X", c));
            }
            return builder.toString();
        }

        private String nextNonceCount()
        {
            String padding = "00000000";
            String next = Integer.toHexString(nonceCount.incrementAndGet()).toLowerCase(Locale.ENGLISH);
            return padding.substring(0, padding.length() - next.length()) + next;
        }

        private String newClientNonce()
        {
            byte[] bytes = new byte[8];
            random.nextBytes(bytes);
            return toHexString(bytes);
        }

        private String toHexString(byte[] bytes)
        {
            return TypeUtil.toHexString(bytes).toLowerCase(Locale.ENGLISH);
        }
    }
}
